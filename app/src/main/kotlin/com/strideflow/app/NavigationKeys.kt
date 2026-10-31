package com.strideflow.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object DashboardNavKey : NavKey

@Serializable
data class ActiveRunNavKey(val workoutId: String? = null) : NavKey

@Serializable
data class PostRunNavKey(val runId: String) : NavKey

@Serializable
data object PlanSetupNavKey : NavKey

@Serializable
data object PlanCalendarNavKey : NavKey

@Serializable
data object HistoryNavKey : NavKey

@Serializable
data object PerformanceNavKey : NavKey

@Serializable
data object SettingsNavKey : NavKey
