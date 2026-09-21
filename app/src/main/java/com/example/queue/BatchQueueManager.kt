package com.example.queue

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.engine.ImageProcessingEngine
import com.example.model.ImageJob
import com.example.model.JobStatus
import com.example.model.UpscaleSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BatchQueueManager(
    private val context: Context,
    private val engine: ImageProcessingEngine,
    private val scope: CoroutineScope
) {
    private val _jobs = MutableStateFlow<List<ImageJob>>(emptyList())
    val jobs: StateFlow<List<ImageJob>> = _jobs.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val activeJobHandles = ConcurrentHashMap<String, Job>()
    private var queueProcessorJob: Job? = null

    fun addJobs(newJobs: List<ImageJob>) {
        _jobs.update { current -> current + newJobs }
    }

    fun removeJob(jobId: String) {
        cancelJob(jobId)
        _jobs.update { current -> current.filter { it.id != jobId } }
    }

    fun clearAll() {
        cancelAll()
        _jobs.value = emptyList()
    }

    fun cancelJob(jobId: String) {
        activeJobHandles[jobId]?.cancel()
        activeJobHandles.remove(jobId)
        _jobs.update { list ->
            list.map { if (it.id == jobId && it.status != JobStatus.COMPLETED) it.copy(status = JobStatus.CANCELLED, stage = "Cancelled") else it }
        }
    }

    fun cancelAll() {
        queueProcessorJob?.cancel()
        activeJobHandles.values.forEach { it.cancel() }
        activeJobHandles.clear()
        _isProcessing.value = false
        _isPaused.value = false
        _jobs.update { list ->
            list.map { if (it.status == JobStatus.PROCESSING || it.status == JobStatus.QUEUED) it.copy(status = JobStatus.CANCELLED, stage = "Cancelled") else it }
        }
    }

    fun pauseQueue() {
        _isPaused.value = true
    }

    fun resumeQueue(settings: UpscaleSettings) {
        _isPaused.value = false
        if (!_isProcessing.value) {
            startProcessing(settings)
        }
    }

    fun startProcessing(settings: UpscaleSettings) {
        if (_isProcessing.value && !_isPaused.value) return

        _isProcessing.value = true
        _isPaused.value = false

        queueProcessorJob = scope.launch(Dispatchers.Default) {
            // Adaptive concurrency: For 3x-10x or large images, process sequentially (1 worker)
            // to maximize available memory and prevent OutOfMemory crashes.
            val isHighScaleOrLarge = settings.scale >= 3 || _jobs.value.any { (it.originalWidth.toLong() * it.originalHeight) >= 4_000_000L }
            val maxWorkers = if (isHighScaleOrLarge) 1 else settings.maxConcurrentWorkers.coerceIn(1, 2)
            val semaphore = Semaphore(maxWorkers)

            while (_isProcessing.value) {
                if (_isPaused.value) {
                    kotlinx.coroutines.delay(250)
                    continue
                }

                // Find next queued job
                val nextJob = _jobs.value.firstOrNull { it.status == JobStatus.QUEUED }
                if (nextJob == null) {
                    // Check if any job is currently processing
                    val anyProcessing = _jobs.value.any { it.status == JobStatus.PROCESSING }
                    if (!anyProcessing) {
                        _isProcessing.value = false
                        break
                    }
                    kotlinx.coroutines.delay(200)
                    continue
                }

                // Launch worker
                val jobWorker = launch {
                    semaphore.withPermit {
                        if (_isPaused.value) return@withPermit
                        processSingleJob(nextJob.id, settings)
                    }
                }
                activeJobHandles[nextJob.id] = jobWorker
            }
        }
    }

    private suspend fun processSingleJob(jobId: String, settings: UpscaleSettings) {
        val currentJob = _jobs.value.firstOrNull { it.id == jobId } ?: return
        if (currentJob.status == JobStatus.CANCELLED) return

        updateJobState(jobId) {
            it.copy(
                status = JobStatus.PROCESSING,
                progress = 0.05f,
                stage = "Starting..."
            )
        }

        try {
            val result = engine.processJob(currentJob, settings) { progress, stage ->
                updateJobState(jobId) {
                    it.copy(progress = progress, stage = stage)
                }
            }

            updateJobState(jobId) {
                it.copy(
                    status = JobStatus.COMPLETED,
                    progress = 1.0f,
                    stage = "Completed",
                    outputWidth = result.targetWidth,
                    outputHeight = result.targetHeight,
                    outputFileSize = result.outputFileSize,
                    outputPath = result.outputFile.absolutePath,
                    outputThumbnail = result.thumbnail,
                    processingTimeMs = result.elapsedTimeMs,
                    scaleUsed = settings.scale,
                    algorithmUsed = settings.algorithm,
                    outputFormatUsed = result.formatUsed,
                    outputQualityUsed = result.qualityUsed,
                    isOptimized = result.isOptimized
                )
            }
        } catch (e: CancellationException) {
            updateJobState(jobId) {
                it.copy(status = JobStatus.CANCELLED, stage = "Cancelled")
            }
        } catch (t: Throwable) {
            val errorMsg = when (t) {
                is OutOfMemoryError -> "${settings.scale}× output is too large for this device's available memory."
                else -> t.message ?: "Processing failed"
            }
            updateJobState(jobId) {
                it.copy(
                    status = JobStatus.FAILED,
                    stage = "Failed: $errorMsg",
                    errorMessage = errorMsg
                )
            }
        } finally {
            activeJobHandles.remove(jobId)
            System.gc()
        }
    }

    private fun updateJobState(jobId: String, transform: (ImageJob) -> ImageJob) {
        _jobs.update { list ->
            list.map { if (it.id == jobId) transform(it) else it }
        }
    }

    /**
     * Exports all completed images into a local ZIP archive.
     */
    suspend fun exportAllAsZip(): File? = withContext(Dispatchers.IO) {
        val completedJobs = _jobs.value.filter { it.status == JobStatus.COMPLETED && it.outputPath != null }
        if (completedJobs.isEmpty()) return@withContext null

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val zipFile = File(context.cacheDir, "Upscaled_Batch_$timeStamp.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val buffer = ByteArray(8192)
            for (job in completedJobs) {
                val sourceFile = File(job.outputPath ?: continue)
                if (!sourceFile.exists()) continue

                val zipEntry = ZipEntry(sourceFile.name)
                zos.putNextEntry(zipEntry)
                FileInputStream(sourceFile).use { fis ->
                    var length: Int
                    while (fis.read(buffer).also { length = it } > 0) {
                        zos.write(buffer, 0, length)
                    }
                }
                zos.closeEntry()
            }
        }
        zipFile
    }

    /**
     * Saves an individual output file to device MediaStore (Pictures/Upscaled).
     */
    suspend fun saveImageToGallery(filePath: String, displayName: String, mimeType: String): Uri? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext null

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Upscaled")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return@withContext null

        resolver.openOutputStream(uri)?.use { outStream ->
            FileInputStream(file).use { inStream ->
                inStream.copyTo(outStream)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        uri
    }

    /**
     * Creates a Share Intent for a completed file using FileProvider.
     */
    fun createShareIntent(file: File, mimeType: String): Intent {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
