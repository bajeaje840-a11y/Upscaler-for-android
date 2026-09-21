package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.model.FilterLevel
import com.example.model.OutputFormat
import com.example.model.UpscaleAlgorithm
import com.example.model.UpscaleSettings
import com.example.ui.theme.BorderStrong
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ResoMaxAccent
import com.example.ui.theme.ResoMaxBackground
import com.example.ui.theme.ResoMaxSurface
import com.example.ui.theme.ResoMaxSurfaceElevated
import com.example.ui.theme.ResoMaxSurfaceHighlight
import com.example.ui.theme.TextDisabled
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsView(
    settings: UpscaleSettings,
    onSettingsChanged: (UpscaleSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Header
        Column {
            Text(
                text = "Engine & Export Settings",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Configure mathematical interpolation kernels, encoding, and spatial filters",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }

        // Section 1: Resampling Algorithm
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Resampling Algorithm",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(ResoMaxSurface)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
            ) {
                UpscaleAlgorithm.entries.forEachIndexed { index, algo ->
                    val isSelected = settings.algorithm == algo
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSettingsChanged(settings.copy(algorithm = algo)) }
                            .background(if (isSelected) ResoMaxSurfaceHighlight else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = algo.displayName,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Text(
                                text = algo.description,
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }

                        // Radio indicator
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.5.dp, if (isSelected) ResoMaxAccent else TextDisabled, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ResoMaxAccent)
                                )
                            }
                        }
                    }

                    if (index < UpscaleAlgorithm.entries.size - 1) {
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
                    }
                }
            }
        }

        // Section 2: Output Format & Compression
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Export Format",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutputFormat.entries.forEach { fmt ->
                    val isSelected = settings.outputFormat == fmt
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) ResoMaxSurfaceElevated else ResoMaxSurface)
                            .border(1.dp, if (isSelected) ResoMaxAccent else BorderSubtle, RoundedCornerShape(6.dp))
                            .clickable { onSettingsChanged(settings.copy(outputFormat = fmt)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (fmt == OutputFormat.AUTO) "Auto (Match)" else fmt.displayName.substringBefore(" "),
                            color = if (isSelected) TextPrimary else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            // Quality slider for JPEG / WebP / Auto
            if (settings.outputFormat == OutputFormat.JPEG || settings.outputFormat == OutputFormat.AUTO) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("JPEG Quality", color = TextSecondary, fontSize = 12.sp)
                    Text("${settings.jpegQuality}%", color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = settings.jpegQuality.toFloat(),
                    onValueChange = { onSettingsChanged(settings.copy(jpegQuality = it.toInt())) },
                    valueRange = 70f..100f,
                    steps = 5,
                    colors = SliderDefaults.colors(
                        thumbColor = ResoMaxAccent,
                        activeTrackColor = ResoMaxAccent,
                        inactiveTrackColor = BorderStrong
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (settings.outputFormat == OutputFormat.WEBP || settings.outputFormat == OutputFormat.AUTO) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("WebP Quality", color = TextSecondary, fontSize = 12.sp)
                    Text("${settings.webpQuality}%", color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = settings.webpQuality.toFloat(),
                    onValueChange = { onSettingsChanged(settings.copy(webpQuality = it.toInt())) },
                    valueRange = 70f..100f,
                    steps = 5,
                    colors = SliderDefaults.colors(
                        thumbColor = ResoMaxAccent,
                        activeTrackColor = ResoMaxAccent,
                        inactiveTrackColor = BorderStrong
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Section 2.5: Optimize File Size Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ResoMaxSurface)
                .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Optimize File Size", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (settings.optimizeFileSize) {
                        "ON: Visually loss-minimized compression with more efficient file size"
                    } else {
                        "OFF: Maximum quality / minimum processing"
                    },
                    color = if (settings.optimizeFileSize) ResoMaxAccent else TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
            Switch(
                checked = settings.optimizeFileSize,
                onCheckedChange = { onSettingsChanged(settings.copy(optimizeFileSize = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ResoMaxAccent,
                    uncheckedTrackColor = ResoMaxBackground,
                    uncheckedBorderColor = BorderStrong
                )
            )
        }

        // Section 3: Filter Tuning
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Post-Processing Filters",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(ResoMaxSurface)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column {
                    Text("Edge-Preserving Sharpen (Unsharp Mask)", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    SegmentedFilterRow(
                        current = settings.sharpening,
                        onSelected = { onSettingsChanged(settings.copy(sharpening = it)) }
                    )
                }

                Column {
                    Text("Bilateral Noise Reduction", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    SegmentedFilterRow(
                        current = settings.noiseReduction,
                        onSelected = { onSettingsChanged(settings.copy(noiseReduction = it)) }
                    )
                }

                Column {
                    Text("Micro-Contrast Clarity", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    SegmentedFilterRow(
                        current = settings.detailEnhancement,
                        onSelected = { onSettingsChanged(settings.copy(detailEnhancement = it)) }
                    )
                }
            }
        }

        // Section 4: Alpha Transparency
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ResoMaxSurface)
                .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Preserve Alpha Transparency", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text("Preserve transparent channels for PNG and WebP assets", color = TextMuted, fontSize = 10.sp)
            }
            Switch(
                checked = settings.preserveTransparency,
                onCheckedChange = { onSettingsChanged(settings.copy(preserveTransparency = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ResoMaxAccent,
                    uncheckedTrackColor = ResoMaxBackground,
                    uncheckedBorderColor = BorderStrong
                )
            )
        }

        // Section 5: Architecture & Privacy Notice
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ResoMaxSurface)
                .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                .padding(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = ResoMaxAccent,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = "100% LOCAL DEVICE PROCESSING",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "ResoMax calculates all scaling using client-side mathematical convolution algorithms (Lanczos, Bicubic). Zero images or metadata leave your device. Fully compliant with editorial and stock photo submission standards.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}
