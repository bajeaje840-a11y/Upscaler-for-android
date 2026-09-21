package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppTab
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ResoMaxAccent
import com.example.ui.theme.ResoMaxSurface
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TextDisabled
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ResoMaxBottomNav(
    activeTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    queueCount: Int,
    completedCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ResoMaxSurface)
    ) {
        HorizontalDivider(thickness = 1.dp, color = BorderSubtle)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
            NavItem(
                label = "Queue",
                icon = Icons.Default.FilterNone,
                badgeCount = queueCount,
                isSelected = activeTab == AppTab.QUEUE,
                onClick = { onTabSelected(AppTab.QUEUE) },
                testTag = "nav_tab_queue"
            )

            NavItem(
                label = "Inspector",
                icon = Icons.Default.Compare,
                badgeCount = 0,
                isSelected = activeTab == AppTab.PREVIEW,
                onClick = { onTabSelected(AppTab.PREVIEW) },
                testTag = "nav_tab_inspector"
            )

            NavItem(
                label = "Completed",
                icon = Icons.Default.CheckCircle,
                badgeCount = completedCount,
                badgeColor = StatusSuccess,
                isSelected = activeTab == AppTab.COMPLETED,
                onClick = { onTabSelected(AppTab.COMPLETED) },
                testTag = "nav_tab_completed"
            )

                NavItem(
                    label = "Settings",
                    icon = Icons.Default.Tune,
                    badgeCount = 0,
                    isSelected = activeTab == AppTab.SETTINGS,
                    onClick = { onTabSelected(AppTab.SETTINGS) },
                    testTag = "nav_tab_settings"
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    label: String,
    icon: ImageVector,
    badgeCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    badgeColor: androidx.compose.ui.graphics.Color = ResoMaxAccent
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) ResoMaxAccent else TextSecondary,
                modifier = Modifier.size(20.dp)
            )

            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) TextPrimary else TextMuted
        )
    }
}
