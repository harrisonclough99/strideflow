package com.strideflow.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.strideflow.app.ui.theme.*

enum class MainNavTab {
    COACH,
    PLAN,
    PERFORMANCE,
    HISTORY
}

@Composable
fun AppBottomNavigationBar(
    currentTab: MainNavTab,
    onNavigateToCoach: () -> Unit,
    onNavigateToPlan: () -> Unit,
    onNavigateToPerformance: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    NavigationBar(
        containerColor = DarkSurfaceElevated,
        contentColor = TextPrimary
    ) {
        NavigationBarItem(
            selected = currentTab == MainNavTab.COACH,
            onClick = {
                if (currentTab != MainNavTab.COACH) onNavigateToCoach()
            },
            icon = { Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = "Coach") },
            label = {
                Text(
                    text = "Coach",
                    fontSize = 11.sp,
                    fontWeight = if (currentTab == MainNavTab.COACH) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.Black,
                selectedTextColor = NeonGreen,
                indicatorColor = NeonGreen,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            )
        )
        NavigationBarItem(
            selected = currentTab == MainNavTab.PLAN,
            onClick = {
                if (currentTab != MainNavTab.PLAN) onNavigateToPlan()
            },
            icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Plan") },
            label = {
                Text(
                    text = "Plan",
                    fontSize = 11.sp,
                    fontWeight = if (currentTab == MainNavTab.PLAN) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.Black,
                selectedTextColor = BrightCyan,
                indicatorColor = BrightCyan,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            )
        )
        NavigationBarItem(
            selected = currentTab == MainNavTab.PERFORMANCE,
            onClick = {
                if (currentTab != MainNavTab.PERFORMANCE) onNavigateToPerformance()
            },
            icon = { Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Performance") },
            label = {
                Text(
                    text = "Performance",
                    fontSize = 11.sp,
                    fontWeight = if (currentTab == MainNavTab.PERFORMANCE) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.Black,
                selectedTextColor = NeonGreen,
                indicatorColor = NeonGreen,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            )
        )
        NavigationBarItem(
            selected = currentTab == MainNavTab.HISTORY,
            onClick = {
                if (currentTab != MainNavTab.HISTORY) onNavigateToHistory()
            },
            icon = { Icon(Icons.Default.History, contentDescription = "History") },
            label = {
                Text(
                    text = "History",
                    fontSize = 11.sp,
                    fontWeight = if (currentTab == MainNavTab.HISTORY) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.Black,
                selectedTextColor = TextPrimary,
                indicatorColor = DarkSurface,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary
            )
        )
    }
}
