package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ImageJob
import com.example.model.JobStatus
import com.example.util.formatFileSize
import com.example.ui.theme.BorderStrong
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ResoMaxAccent
import com.example.ui.theme.ResoMaxBackground
import com.example.ui.theme.ResoMaxSurface
import com.example.ui.theme.ResoMaxSurfaceElevated
import com.example.ui.theme.ResoMaxSurfaceHighlight
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TextDisabled
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun BeforeAfterCompareView(
    job: ImageJob?,
    allJobs: List<ImageJob> = emptyList(),
    onSelectJob: (ImageJob) -> Unit = {},
    onSaveImage: (ImageJob) -> Unit,
    onShareImage: (ImageJob) -> Unit,
    modifier: Modifier = Modifier
) {
    if (job == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No image selected",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select any image from the Queue to inspect Before / After.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
        return
    }

    var sliderFraction by remember { mutableFloatStateOf(0.5f) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    val originalThumb = job.originalThumbnail
    val upscaledThumb = job.outputThumbnail ?: job.originalThumbnail

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Quick thumbnail asset carousel (if multiple jobs exist)
        if (allJobs.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                allJobs.forEach { item ->
                    val isSelected = item.id == job.id
                    val thumb = item.outputThumbnail ?: item.originalThumbnail
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ResoMaxSurfaceElevated)
                            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) ResoMaxAccent else BorderSubtle, RoundedCornerShape(4.dp))
                            .clickable { onSelectJob(item) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (thumb != null) {
                            Image(
                                bitmap = thumb.asImageBitmap(),
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("IMG", fontSize = 8.sp, color = TextMuted)
                        }
                    }
                }
            }
        }

        // Top Toolbar: Filename & Zoom Presets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = job.name,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (job.status == JobStatus.COMPLETED) "${job.scaleUsed}× enlargement • ${job.algorithmUsed.shortName}" else "Processing...",
                    color = if (job.status == JobStatus.COMPLETED) StatusSuccess else ResoMaxAccent,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Compact Zoom controls (Lightroom style)
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Percentage badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(ResoMaxSurfaceElevated)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${(zoomScale * 100).toInt()}%",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                IconButton(
                    onClick = { zoomScale = (zoomScale / 1.25f).coerceAtLeast(0.5f) },
                    modifier = Modifier.size(30.dp).testTag("zoom_out_btn")
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = { zoomScale = (zoomScale * 1.25f).coerceAtMost(5.0f) },
                    modifier = Modifier.size(30.dp).testTag("zoom_in_btn")
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = {
                        zoomScale = 1.0f
                        panOffsetX = 0f
                        panOffsetY = 0f
                    },
                    modifier = Modifier.size(30.dp).testTag("reset_zoom_btn")
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "Fit", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Viewport: Precision Split Canvas
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ResoMaxBackground)
                .border(1.dp, BorderStrong, RoundedCornerShape(6.dp))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(0.5f, 6.0f)
                        panOffsetX += pan.x
                        panOffsetY += pan.y
                    }
                }
        ) {
            val dividerX = (constraints.maxWidth * sliderFraction).coerceIn(0f, constraints.maxWidth.toFloat())

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                        translationX = panOffsetX
                        translationY = panOffsetY
                    }
            ) {
                // Background Layer: Original 1x
                if (originalThumb != null) {
                    Image(
                        bitmap = originalThumb.asImageBitmap(),
                        contentDescription = "Original image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                // Foreground Layer: Upscaled (Clipped to left side of slider)
                if (upscaledThumb != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val clipW = size.width * sliderFraction
                        val path = Path().apply {
                            addRect(Rect(0f, 0f, clipW, size.height))
                        }
                        clipPath(path) {
                            drawImage(
                                image = upscaledThumb.asImageBitmap(),
                                dstOffset = IntOffset.Zero,
                                dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                            )
                        }
                    }
                }
            }

            // Hairline Split Divider
            Box(
                modifier = Modifier
                    .offset { IntOffset(dividerX.roundToInt(), 0) }
                    .fillMaxHeight()
                    .width(1.5.dp)
                    .background(Color.White.copy(alpha = 0.9f))
            )

            // Minimalist circular handle
            Box(
                modifier = Modifier
                    .offset { IntOffset(dividerX.roundToInt() - 13, (constraints.maxHeight / 2) - 13) }
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(ResoMaxSurfaceElevated)
                    .border(1.dp, Color.White, CircleShape)
                    .draggable(
                        orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            val newX = (constraints.maxWidth * sliderFraction) + delta
                            sliderFraction = (newX / constraints.maxWidth).coerceIn(0.05f, 0.95f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "◫",
                    color = Color.White,
                    fontSize = 11.sp
                )
            }

            // Floating Header Badges
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ResoMaxSurface.copy(alpha = 0.9f))
                    .border(0.5.dp, BorderStrong, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (job.status == JobStatus.COMPLETED) "UPSCALED (${job.scaleUsed}×)" else "ORIGINAL (1×)",
                    color = if (job.status == JobStatus.COMPLETED) ResoMaxAccent else TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ResoMaxSurface.copy(alpha = 0.9f))
                    .border(0.5.dp, BorderStrong, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "ORIGINAL (1×)",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Professional Technical Specs Panel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ResoMaxSurface)
                .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                .padding(10.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Original specs
                    Column {
                        Text("Original", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text("${job.originalWidth} × ${job.originalHeight}", color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Text(formatFileSize(job.originalFileSize), color = TextSecondary, fontSize = 11.sp)
                    }

                    // Divider
                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(BorderSubtle))

                    // Output specs
                    Column {
                        Text("Upscaled (${job.scaleUsed}×)", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text("${job.outputWidth} × ${job.outputHeight}", color = StatusSuccess, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                        val outSizeStr = if (job.outputFileSize > 0) formatFileSize(job.outputFileSize) else "Pending"
                        Text(outSizeStr, color = TextSecondary, fontSize = 11.sp)
                    }

                    // Divider
                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(BorderSubtle))

                    // Algorithm & Time
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Algorithm", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text(job.algorithmUsed.shortName, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(if (job.processingTimeMs > 0) "${job.processingTimeMs}ms" else "-", color = TextSecondary, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Mobile Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onSaveImage(job) },
                        enabled = job.status == JobStatus.COMPLETED && job.outputPath != null,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("save_image_btn"),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ResoMaxAccent,
                            disabledContainerColor = ResoMaxSurfaceElevated
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save to Gallery", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = { onShareImage(job) },
                        enabled = job.status == JobStatus.COMPLETED && job.outputPath != null,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("share_image_btn"),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderStrong),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share...", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
