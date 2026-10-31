package com.strideflow.app

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.strideflow.app.ui.dashboard.DashboardScreen
import com.strideflow.app.ui.history.HistoryScreen
import com.strideflow.app.ui.plan.PlanCalendarScreen
import com.strideflow.app.ui.postrun.PostRunScreen
import com.strideflow.app.ui.run.ActiveRunScreen
import com.strideflow.app.ui.setup.PlanSetupScreen
import com.strideflow.app.ui.settings.SettingsScreen

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(DashboardNavKey)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<DashboardNavKey> {
                DashboardScreen(
                    onStartRun = { workoutId -> backStack.add(ActiveRunNavKey(workoutId)) },
                    onOpenPlanSetup = { backStack.add(PlanSetupNavKey) },
                    onOpenPlanCalendar = { backStack.add(PlanCalendarNavKey) },
                    onOpenHistory = { backStack.add(HistoryNavKey) },
                    onOpenSettings = { backStack.add(SettingsNavKey) }
                )
            }
            entry<ActiveRunNavKey> { key ->
                ActiveRunScreen(
                    workoutId = key.workoutId,
                    onRunFinished = { runId ->
                        backStack.removeLastOrNull()
                        backStack.add(PostRunNavKey(runId))
                    },
                    onCancelRun = {
                        backStack.removeLastOrNull()
                    }
                )
            }
            entry<PostRunNavKey> { key ->
                PostRunScreen(
                    runId = key.runId,
                    onReturnToDashboard = {
                        backStack.removeLastOrNull()
                    }
                )
            }
            entry<PlanSetupNavKey> {
                PlanSetupScreen(
                    onPlanCreated = { backStack.removeLastOrNull() },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<PlanCalendarNavKey> {
                PlanCalendarScreen(
                    onSelectWorkoutToRun = { workoutId ->
                        backStack.add(ActiveRunNavKey(workoutId))
                    },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<HistoryNavKey> {
                HistoryScreen(
                    onSelectRun = { runId -> backStack.add(PostRunNavKey(runId)) },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<SettingsNavKey> {
                SettingsScreen(
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
