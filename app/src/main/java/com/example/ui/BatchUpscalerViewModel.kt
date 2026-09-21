package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.ImageProcessingEngine
import com.example.model.ImageJob
import com.example.model.JobStatus
import com.example.model.MemorySafetyCheck
import com.example.model.UpscaleAlgorithm
import com.example.model.UpscaleSettings
import com.example.queue.BatchQueueManager
import com.example.util.ExifHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class AppTab {
    QUEUE,
    PREVIEW,
    COMPLETED,
    SETTINGS;

    companion object {
        val BATCH = QUEUE
    }
}

data class BatchProgress(
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
    val overallPercentage: Float = 0.0f
)

class BatchUpscalerViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    val engine = ImageProcessingEngine(context)
    val queueManager = BatchQueueManager(context, engine, viewModelScope)

    val jobs: StateFlow<List<ImageJob>> = queueManager.jobs
    val isProcessing: StateFlow<Boolean> = queueManager.isProcessing
    val isPaused: StateFlow<Boolean> = queueManager.isPaused

    private val _settings = MutableStateFlow(UpscaleSettings())
    val settings: StateFlow<UpscaleSettings> = _settings.asStateFlow()

    private val _selectedJobForPreview = MutableStateFlow<ImageJob?>(null)
    val selectedJobForPreview: StateFlow<ImageJob?> = _selectedJobForPreview.asStateFlow()

    private val _activeTab = MutableStateFlow(AppTab.BATCH)
    val activeTab: StateFlow<AppTab> = _activeTab.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    val batchProgress: StateFlow<BatchProgress> = jobs.mapBatchProgress()
        .stateIn(viewModelScope, SharingStarted.Lazily, BatchProgress())

    fun setActiveTab(tab: AppTab) {
        _activeTab.value = tab
    }

    fun updateSettings(newSettings: UpscaleSettings) {
        _settings.value = newSettings
    }

    fun setScale(scale: Int) {
        _settings.value = _settings.value.copy(scale = scale)
    }

    fun setAlgorithm(algorithm: UpscaleAlgorithm) {
        _settings.value = _settings.value.copy(algorithm = algorithm)
    }

    fun addPickedUris(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val newJobs = mutableListOf<ImageJob>()
            for (uri in uris) {
                try {
                    var displayName = "image_${UUID.randomUUID().toString().take(6)}.jpg"
                    var fileSize = 0L

                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1) displayName = cursor.getString(nameIndex) ?: displayName
                            if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                        }
                    }

                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"

                    // Use ExifHelper to get true visual dimensions accounting for EXIF orientation (e.g. 90/270 degree rotation)
                    val (origW, origH) = ExifHelper.getTrueDimensions(context, uri, null)

                    // Decode lightweight thumbnail with orientation applied
                    val sampleSize = calculateInSampleSize(origW, origH, 300, 300)
                    val thumb = ExifHelper.decodeOrientedBitmap(context, uri, null, sampleSize)

                    newJobs.add(
                        ImageJob(
                            id = UUID.randomUUID().toString(),
                            name = displayName,
                            uri = uri,
                            originalWidth = origW,
                            originalHeight = origH,
                            originalFileSize = if (fileSize > 0) fileSize else (origW * origH * 2L),
                            originalMimeType = mimeType,
                            originalThumbnail = thumb,
                            scaleUsed = _settings.value.scale,
                            algorithmUsed = _settings.value.algorithm
                        )
                    )
                } catch (e: Exception) {
                    _toastEvent.emit("Failed to load file: ${e.message}")
                }
            }

            if (newJobs.isNotEmpty()) {
                queueManager.addJobs(newJobs)
                if (_selectedJobForPreview.value == null) {
                    _selectedJobForPreview.value = newJobs.first()
                }
            }
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun startBatch() {
        queueManager.startProcessing(_settings.value)
    }

    fun pauseBatch() {
        queueManager.pauseQueue()
    }

    fun resumeBatch() {
        queueManager.resumeQueue(_settings.value)
    }

    fun cancelBatch() {
        queueManager.cancelAll()
    }

    fun cancelJob(jobId: String) {
        queueManager.cancelJob(jobId)
    }

    fun removeJob(jobId: String) {
        queueManager.removeJob(jobId)
        if (_selectedJobForPreview.value?.id == jobId) {
            _selectedJobForPreview.value = jobs.value.firstOrNull { it.id != jobId }
        }
    }

    fun clearAll() {
        queueManager.clearAll()
        _selectedJobForPreview.value = null
    }

    fun selectForPreview(job: ImageJob) {
        _selectedJobForPreview.value = job
        _activeTab.value = AppTab.PREVIEW
    }

    fun exportAllAsZip(onComplete: (File?) -> Unit) {
        viewModelScope.launch {
            val zipFile = queueManager.exportAllAsZip()
            onComplete(zipFile)
            if (zipFile != null) {
                _toastEvent.emit("Exported all to ZIP: ${zipFile.name}")
            } else {
                _toastEvent.emit("No completed images to export.")
            }
        }
    }

    fun saveSingleImage(job: ImageJob, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val path = job.outputPath
            if (path == null) {
                onComplete(false)
                return@launch
            }
            val uri = queueManager.saveImageToGallery(path, File(path).name, _settings.value.outputFormat.mimeType)
            val success = uri != null
            if (success) {
                _toastEvent.emit("Saved ${job.name} to Pictures/Upscaled")
            } else {
                _toastEvent.emit("Failed to save image")
            }
            onComplete(success)
        }
    }

    fun checkMemorySafety(): MemorySafetyCheck {
        val maxOriginalPixels = jobs.value.maxOfOrNull { it.originalWidth.toLong() * it.originalHeight.toLong() } ?: (800L * 600L)
        val origW = kotlin.math.sqrt(maxOriginalPixels.toDouble()).toInt()
        return engine.checkMemorySafety(origW, origW, _settings.value.scale)
    }
}

private fun StateFlow<List<ImageJob>>.mapBatchProgress() = kotlinx.coroutines.flow.flow {
    collect { list ->
        if (list.isEmpty()) {
            emit(BatchProgress())
        } else {
            val completed = list.count { it.status == JobStatus.COMPLETED }
            val failed = list.count { it.status == JobStatus.FAILED }
            val total = list.size
            val progressSum = list.sumOf { it.progress.toDouble() }.toFloat()
            val overall = (progressSum / total).coerceIn(0f, 1f)
            emit(
                BatchProgress(
                    totalCount = total,
                    completedCount = completed,
                    failedCount = failed,
                    overallPercentage = overall
                )
            )
        }
    }
}
