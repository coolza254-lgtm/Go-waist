package com.gowaist.app.ui.nav

import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute
@Serializable data object HomeRoute
@Serializable data object RunTabRoute
@Serializable data object TrainTabRoute
@Serializable data object PlanTabRoute
@Serializable data object GoalsTabRoute

@Serializable data object SettingsRoute
@Serializable data object BodyRoute

@Serializable data class RunEditRoute(val runId: Long = 0)
@Serializable data object RunImportRoute
@Serializable data class RunDetailRoute(val id: Long)

@Serializable data class ExerciseDetailRoute(val id: Long)
@Serializable data class ExerciseEditRoute(val id: Long = 0)
@Serializable data class ChainEditRoute(val id: Long = 0)
@Serializable data class TemplateEditRoute(val id: Long = 0)

@Serializable data class SessionRoute(val id: Long)
@Serializable data class SessionSummaryRoute(val id: Long)
@Serializable data class IntervalTimerRoute(val planDayId: Long = 0)

@Serializable data object PlanCreateRoute
@Serializable data class GoalEditRoute(val id: Long = 0)

@Serializable data object RunLogRoute
/** [type] is a [com.gowaist.core.perf.RunType] name. */
@Serializable data class RunProgramRoute(val type: String)
@Serializable data object Vo2Route
@Serializable data object PerformanceRoute
