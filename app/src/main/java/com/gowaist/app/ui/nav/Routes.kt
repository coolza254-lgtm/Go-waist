package com.gowaist.app.ui.nav

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object HistoryRoute
@Serializable data object ProgressRoute
@Serializable data object ShoesRoute

@Serializable data object SettingsRoute
@Serializable data object BodyRoute
@Serializable data object Vo2Route
@Serializable data object PerformanceRoute
@Serializable data object GoalsRoute
@Serializable data class GoalEditRoute(val id: Long = 0)

@Serializable data class RunEditRoute(val runId: Long = 0)
@Serializable data object RunImportRoute
@Serializable data class RunDetailRoute(val id: Long)

@Serializable data class ShoeEditRoute(val id: Long = 0)
