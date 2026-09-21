package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ImageJob
import com.example.model.JobStatus
import com.example.util.formatFileSize
import com.example.ui.theme.AccentPrimary
import com.example.ui.theme.BorderStrong
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceHighlight
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TextDisabled
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun BatchItemCard(
    job: ImageJob,
    onInspectPreview: (ImageJob) -> Unit,
    onSaveImage: (ImageJob) -> Unit,
    onCancelJob: (String) -> Unit,
    onRemoveJob: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(DarkSurface)
            .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
            .clickable { onInspectPreview(job) }
            .testTag("batch_item_${job.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Precise Asset Thumbnail (44x44 with 4dp corner)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkSurfaceElevated)
                        .border(1.dp, BorderStrong, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val thumb = job.outputThumbnail ?: job.originalThumbnail
                    if (thumb != null) {
                        Image(
                            bitmap = thumb.asImageBitmap(),
                            contentDescription = job.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = "IMG",
                            color = TextDisabled,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // File Information & Dimensions
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = job.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // File format pill
                        val ext = job.name.substringAfterLast(".", "IMG").uppercase()
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(DarkSurfaceHighlight)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = ext,
                                color = TextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Resolutions: In -> Out
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${job.originalWidth} × ${job.originalHeight}",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "→",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "${job.outputWidth} × ${job.outputHeight}",
                            color = if (job.status == JobStatus.COMPLETED) StatusSuccess else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = if (job.status == JobStatus.COMPLETED) FontWeight.SemiBold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "(${job.scaleUsed}×)",
                            color = AccentPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Status line
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (job.status) {
                            JobStatus.COMPLETED -> {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Ready",
                                    tint = StatusSuccess,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Ready (${job.processingTimeMs}ms • ${formatFileSize(job.outputFileSize)})",
                                    color = StatusSuccess,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                            JobStatus.PROCESSING -> {
                                Text(
                                    text = job.stage,
                                    color = AccentPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            JobStatus.QUEUED -> {
                                Text(
                                    text = "Queued",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            JobStatus.CANCELLED -> {
                                Text(
                                    text = "Cancelled",
                                    color = TextDisabled,
                                    fontSize = 11.sp
                                )
                            }
                            JobStatus.FAILED -> {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = "Failed",
                                    tint = StatusError,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = job.stage,
                                    color = StatusError,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Row Actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (job.status == JobStatus.COMPLETED) {
                        IconButton(
                            onClick = { onInspectPreview(job) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.OpenInFull,
                                contentDescription = "Inspect",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = { onSaveImage(job) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Save file",
                                tint = StatusSuccess,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else if (job.status == JobStatus.PROCESSING) {
                        IconButton(
                            onClick = { onCancelJob(job.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = StatusError,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { onRemoveJob(job.id) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Real-time progress bar for active item
            AnimatedVisibility(visible = job.status == JobStatus.PROCESSING) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    LinearProgressIndicator(
                        progress = { job.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp)),
                        color = AccentPrimary,
                        trackColor = BorderSubtle
                    )
                }
            }
        }
    }
}
