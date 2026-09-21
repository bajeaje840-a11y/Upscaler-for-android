package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.ui.theme.AccentPrimary
import com.example.ui.theme.BorderStrong
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceHighlight
import com.example.ui.theme.TextDisabled
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: UpscaleSettings,
    onSettingsChanged: (UpscaleSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header with title and close action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Processing Settings",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Configure mathematical resampling engine & export parameters",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp).testTag("close_settings_btn")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: Resampling Algorithm
            Text(
                text = "Resampling Algorithm",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(DarkBackground)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp))
            ) {
                UpscaleAlgorithm.entries.forEachIndexed { index, algo ->
                    val isSelected = settings.algorithm == algo
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSettingsChanged(settings.copy(algorithm = algo)) }
                            .background(if (isSelected) DarkSurfaceHighlight else Color.Transparent)
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

                        // Radio dot
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .border(1.5.dp, if (isSelected) AccentPrimary else TextDisabled, RoundedCornerShape(7.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(AccentPrimary)
                                )
                            }
                        }
                    }

                    if (index < UpscaleAlgorithm.entries.size - 1) {
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Section 2: Output Format
            Text(
                text = "Export Format",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutputFormat.entries.forEach { fmt ->
                    val isSelected = settings.outputFormat == fmt
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) DarkSurfaceElevated else DarkBackground)
                            .border(1.dp, if (isSelected) BorderStrong else BorderSubtle, RoundedCornerShape(4.dp))
                            .clickable { onSettingsChanged(settings.copy(outputFormat = fmt)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = fmt.displayName,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            // Quality slider for JPEG / WebP
            if (settings.outputFormat == OutputFormat.JPEG) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Compression Quality", color = TextSecondary, fontSize = 12.sp)
                    Text("${settings.jpegQuality}%", color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = settings.jpegQuality.toFloat(),
                    onValueChange = { onSettingsChanged(settings.copy(jpegQuality = it.toInt())) },
                    valueRange = 70f..100f,
                    steps = 5,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentPrimary,
                        activeTrackColor = AccentPrimary,
                        inactiveTrackColor = BorderStrong
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (settings.outputFormat == OutputFormat.WEBP) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Compression Quality", color = TextSecondary, fontSize = 12.sp)
                    Text("${settings.webpQuality}%", color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = settings.webpQuality.toFloat(),
                    onValueChange = { onSettingsChanged(settings.copy(webpQuality = it.toInt())) },
                    valueRange = 70f..100f,
                    steps = 5,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentPrimary,
                        activeTrackColor = AccentPrimary,
                        inactiveTrackColor = BorderStrong
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 3: Filter Tuning
            Text("Sharpening (Unsharp Mask)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            SegmentedFilterRow(
                current = settings.sharpening,
                onSelected = { onSettingsChanged(settings.copy(sharpening = it)) }
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("Bilateral Noise Reduction", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            SegmentedFilterRow(
                current = settings.noiseReduction,
                onSelected = { onSettingsChanged(settings.copy(noiseReduction = it)) }
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("Micro-Contrast Clarity", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            SegmentedFilterRow(
                current = settings.detailEnhancement,
                onSelected = { onSettingsChanged(settings.copy(detailEnhancement = it)) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Section 4: Alpha Transparency
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Preserve Alpha Transparency", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("Preserve alpha channel transparency on PNG and WebP files", color = TextMuted, fontSize = 10.sp)
                }
                Switch(
                    checked = settings.preserveTransparency,
                    onCheckedChange = { onSettingsChanged(settings.copy(preserveTransparency = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentPrimary,
                        uncheckedTrackColor = DarkBackground,
                        uncheckedBorderColor = BorderStrong
                    )
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Section 5: Technical Architecture Information
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(DarkBackground)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Text(
                        text = "ON-DEVICE EXECUTION & COMPLIANCE",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Processing is executed completely locally on your device via standard mathematical resampling and 2-pass spatial convolutions. No external cloud servers or AI generative models are used. Suitable for preparing high-resolution assets for stock agencies (e.g. Adobe Stock, Shutterstock).",
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SegmentedFilterRow(
    current: FilterLevel,
    onSelected: (FilterLevel) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(DarkBackground)
            .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp)),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        FilterLevel.entries.forEachIndexed { index, level ->
            val isSelected = current == level
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (isSelected) DarkSurfaceHighlight else Color.Transparent)
                    .clickable { onSelected(level) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = level.displayName,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) TextPrimary else TextSecondary
                )
            }
            if (index < FilterLevel.entries.size - 1) {
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(BorderSubtle))
            }
        }
    }
}
