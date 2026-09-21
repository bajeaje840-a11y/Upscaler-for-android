package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ImageJob
import com.example.model.JobStatus
import com.example.model.UpscaleSettings
import com.example.ui.BatchProgress
import com.example.ui.theme.BorderStrong
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ResoMaxAccent
import com.example.ui.theme.ResoMaxBackground
import com.example.ui.theme.ResoMaxSurface
import com.example.ui.theme.ResoMaxSurfaceCard
import com.example.ui.theme.ResoMaxSurfaceElevated
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TextDisabled
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun BatchQueueView(
    jobs: List<ImageJob>,
    isProcessing: Boolean,
    isPaused: Boolean,
    batchProgress: BatchProgress,
    settings: UpscaleSettings,
    onScaleSelected: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onSelectImages: () -> Unit,
    onStartUpscaling: () -> Unit,
    onPauseBatch: () -> Unit,
    onResumeBatch: () -> Unit,
    onCancelBatch: () -> Unit,
    onClearAll: () -> Unit,
    onInspectJob: (ImageJob) -> Unit,
    onSaveJob: (ImageJob) -> Unit,
    onCancelJob: (String) -> Unit,
    onRemoveJob: (String) -> Unit,
    onExportAllZip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Upload & Parameters Control Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ResoMaxSurface)
                .border(width = 1.dp, color = BorderSubtle)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Upload Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (jobs.isEmpty()) "Image Queue" else "${jobs.size} ${if (jobs.size == 1) "image" else "images"} queued",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "JPG, PNG, WebP • Batch processing",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Button(
                        onClick = onSelectImages,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ResoMaxAccent),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("select_images_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Images", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))

                // Scale Selector Row
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Scale Factor",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        // Resolution Calculation indicator
                        val queuedJob = jobs.firstOrNull()
                        val outputDimText = if (queuedJob != null) {
                            "Output: ~${queuedJob.originalWidth * settings.scale} × ${queuedJob.originalHeight * settings.scale}"
                        } else {
                            "Output: ${settings.scale}× dimensions"
                        }

                        Text(
                            text = outputDimText,
                            color = ResoMaxAccent,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Mobile touch-friendly scale selector
                    val scales = listOf(2, 3, 4, 5, 6, 7, 8, 9, 10)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ResoMaxBackground)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        scales.forEachIndexed { index, scale ->
                            val isSelected = scale == settings.scale
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .width(42.dp)
                                    .background(if (isSelected) ResoMaxAccent else Color.Transparent)
                                    .clickable { onScaleSelected(scale) }
                                    .testTag("scale_${scale}x_btn"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${scale}×",
                                    color = if (isSelected) Color.White else TextSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            if (index < scales.size - 1) {
                                Box(modifier = Modifier.width(1.dp).fillMaxSize().background(BorderSubtle))
                            }
                        }
                    }
                }

                // Processing Parameter Summary Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(ResoMaxSurfaceElevated)
                        .clickable { onOpenSettings() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Engine:",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "${settings.algorithm.shortName} • ${settings.outputFormat.displayName} (Q${if (settings.outputFormat.isLossless) "Lossless" else settings.jpegQuality.toString()})",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(
                        text = "Customize",
                        color = ResoMaxAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Live Processing Progress Indicator
        AnimatedVisibility(visible = isProcessing || batchProgress.overallPercentage > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ResoMaxSurfaceElevated)
                    .border(width = 1.dp, color = BorderSubtle)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isProcessing) {
                                "Upscaling: ${batchProgress.completedCount} / ${batchProgress.totalCount} images"
                            } else {
                                "Batch Complete: ${batchProgress.completedCount} ready"
                            },
                            color = if (isProcessing) ResoMaxAccent else StatusSuccess,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${(batchProgress.overallPercentage * 100).toInt()}%",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LinearProgressIndicator(
                        progress = { batchProgress.overallPercentage },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp)),
                        color = ResoMaxAccent,
                        trackColor = BorderSubtle
                    )
                }
            }
        }

        // Queue Header (Count + Clear)
        if (jobs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "QUEUE (${jobs.size})",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )

                Text(
                    text = "Clear All",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clickable { onClearAll() }
                        .padding(vertical = 2.dp)
                        .testTag("clear_all_btn")
                )
            }
        }

        // Queue List or Empty State
        if (jobs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ResoMaxSurfaceElevated)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Layers,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Queue is empty",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Add images to start batch upscaling",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = onSelectImages,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ResoMaxAccent),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("empty_state_add_images_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Images", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(jobs, key = { it.id }) { job ->
                    BatchItemCard(
                        job = job,
                        onInspectPreview = onInspectJob,
                        onSaveImage = onSaveJob,
                        onCancelJob = onCancelJob,
                        onRemoveJob = onRemoveJob
                    )
                }
            }
        }

        // Sticky Bottom Mobile Action Bar (Thumb Zone)
        if (jobs.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ResoMaxSurface)
                    .border(width = 1.dp, color = BorderSubtle)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isProcessing) {
                        Button(
                            onClick = onStartUpscaling,
                            enabled = jobs.any { it.status == JobStatus.QUEUED },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("start_upscaling_btn"),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ResoMaxAccent,
                                disabledContainerColor = ResoMaxSurfaceElevated
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Upscaling", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else if (isPaused) {
                        Button(
                            onClick = onResumeBatch,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ResoMaxAccent)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Resume", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = onCancelBatch,
                            modifier = Modifier
                                .height(44.dp),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderStrong)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel", color = TextPrimary, fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = onPauseBatch,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ResoMaxSurfaceElevated)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pause", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }

                        OutlinedButton(
                            onClick = onCancelBatch,
                            modifier = Modifier
                                .height(44.dp),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderStrong)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel", color = TextPrimary, fontSize = 12.sp)
                        }
                    }

                    if (batchProgress.completedCount > 0) {
                        OutlinedButton(
                            onClick = onExportAllZip,
                            modifier = Modifier
                                .height(44.dp)
                                .testTag("export_zip_btn"),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusSuccess),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ZIP (${batchProgress.completedCount})", color = StatusSuccess, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
