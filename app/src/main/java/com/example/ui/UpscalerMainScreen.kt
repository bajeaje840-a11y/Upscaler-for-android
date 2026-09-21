package com.example.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.model.ImageJob
import com.example.model.JobStatus
import com.example.model.MemorySafetyCheck
import com.example.ui.components.BatchQueueView
import com.example.ui.components.BeforeAfterCompareView
import com.example.ui.components.CompletedView
import com.example.ui.components.ResoMaxBottomNav
import com.example.ui.components.ResoMaxTopBar
import com.example.ui.components.SafetyWarningDialog
import com.example.ui.components.SettingsSheet
import com.example.ui.components.SettingsView
import com.example.ui.theme.ResoMaxBackground
import java.io.File

@Composable
fun UpscalerMainScreen(
    viewModel: BatchUpscalerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val jobs by viewModel.jobs.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val selectedJobForPreview by viewModel.selectedJobForPreview.collectAsState()
    val batchProgress by viewModel.batchProgress.collectAsState()

    var showQuickSettingsSheet by remember { mutableStateOf(false) }
    var safetyWarningCheck by remember { mutableStateOf<MemorySafetyCheck?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 100)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addPickedUris(uris)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    if (showQuickSettingsSheet) {
        SettingsSheet(
            settings = settings,
            onSettingsChanged = { viewModel.updateSettings(it) },
            onDismiss = { showQuickSettingsSheet = false }
        )
    }

    safetyWarningCheck?.let { check ->
        SafetyWarningDialog(
            safetyCheck = check,
            onProceedAnyway = {
                safetyWarningCheck = null
                viewModel.startBatch()
            },
            onAdjustScale = {
                safetyWarningCheck = null
                viewModel.setActiveTab(AppTab.SETTINGS)
            },
            onDismiss = { safetyWarningCheck = null }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ResoMaxBackground,
        topBar = {
            ResoMaxTopBar(
                onOpenSettings = {
                    viewModel.setActiveTab(AppTab.SETTINGS)
                }
            )
        },
        bottomBar = {
            ResoMaxBottomNav(
                activeTab = activeTab,
                onTabSelected = { viewModel.setActiveTab(it) },
                queueCount = jobs.count { it.status == JobStatus.QUEUED || it.status == JobStatus.PROCESSING },
                completedCount = batchProgress.completedCount
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ResoMaxBackground)
        ) {
            when (activeTab) {
                AppTab.QUEUE -> {
                    BatchQueueView(
                        jobs = jobs,
                        isProcessing = isProcessing,
                        isPaused = isPaused,
                        batchProgress = batchProgress,
                        settings = settings,
                        onScaleSelected = { viewModel.setScale(it) },
                        onOpenSettings = { showQuickSettingsSheet = true },
                        onSelectImages = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onStartUpscaling = {
                            val safety = viewModel.checkMemorySafety()
                            if (!safety.isSafe) {
                                safetyWarningCheck = safety
                            } else {
                                viewModel.startBatch()
                            }
                        },
                        onPauseBatch = { viewModel.pauseBatch() },
                        onResumeBatch = { viewModel.resumeBatch() },
                        onCancelBatch = { viewModel.cancelBatch() },
                        onClearAll = { viewModel.clearAll() },
                        onInspectJob = {
                            viewModel.selectForPreview(it)
                        },
                        onSaveJob = { viewModel.saveSingleImage(it) {} },
                        onCancelJob = { viewModel.cancelJob(it) },
                        onRemoveJob = { viewModel.removeJob(it) },
                        onExportAllZip = {
                            viewModel.exportAllAsZip { zipFile ->
                                if (zipFile != null) {
                                    val intent = viewModel.queueManager.createShareIntent(zipFile, "application/zip")
                                    context.startActivity(Intent.createChooser(intent, "Export ResoMax Batch ZIP"))
                                }
                            }
                        }
                    )
                }

                AppTab.PREVIEW -> {
                    val activeJob = selectedJobForPreview ?: jobs.firstOrNull()
                    BeforeAfterCompareView(
                        job = activeJob,
                        allJobs = jobs,
                        onSelectJob = { viewModel.selectForPreview(it) },
                        onSaveImage = { viewModel.saveSingleImage(it) {} },
                        onShareImage = { job ->
                            job.outputPath?.let { path ->
                                val file = File(path)
                                val intent = viewModel.queueManager.createShareIntent(file, settings.outputFormat.mimeType)
                                context.startActivity(Intent.createChooser(intent, "Share Upscaled Asset"))
                            }
                        }
                    )
                }

                AppTab.COMPLETED -> {
                    val completedJobs = jobs.filter { it.status == JobStatus.COMPLETED }
                    CompletedView(
                        completedJobs = completedJobs,
                        onInspectJob = {
                            viewModel.selectForPreview(it)
                        },
                        onSaveJob = { viewModel.saveSingleImage(it) {} },
                        onShareJob = { job ->
                            job.outputPath?.let { path ->
                                val file = File(path)
                                val intent = viewModel.queueManager.createShareIntent(file, settings.outputFormat.mimeType)
                                context.startActivity(Intent.createChooser(intent, "Share Upscaled Asset"))
                            }
                        },
                        onExportAllZip = {
                            viewModel.exportAllAsZip { zipFile ->
                                if (zipFile != null) {
                                    val intent = viewModel.queueManager.createShareIntent(zipFile, "application/zip")
                                    context.startActivity(Intent.createChooser(intent, "Export ResoMax Batch ZIP"))
                                }
                            }
                        },
                        onSwitchToQueue = {
                            viewModel.setActiveTab(AppTab.QUEUE)
                        }
                    )
                }

                AppTab.SETTINGS -> {
                    SettingsView(
                        settings = settings,
                        onSettingsChanged = { viewModel.updateSettings(it) }
                    )
                }
            }
        }
    }
}
