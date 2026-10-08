package com.gowaist.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.gowaist.app.R
import com.gowaist.app.data.settings.AppSettings
import com.gowaist.app.ui.body.BodyScreen
import com.gowaist.app.ui.goals.GoalEditScreen
import com.gowaist.app.ui.goals.GoalsScreen
import com.gowaist.app.ui.home.HomeScreen
import com.gowaist.app.ui.nav.BodyRoute
import com.gowaist.app.ui.nav.ChainEditRoute
import com.gowaist.app.ui.nav.ExerciseDetailRoute
import com.gowaist.app.ui.nav.ExerciseEditRoute
import com.gowaist.app.ui.nav.GoalEditRoute
import com.gowaist.app.ui.nav.GoalsTabRoute
import com.gowaist.app.ui.nav.HomeRoute
import com.gowaist.app.ui.nav.IntervalTimerRoute
import com.gowaist.app.ui.nav.PlanCreateRoute
import com.gowaist.app.ui.nav.PlanTabRoute
import com.gowaist.app.ui.nav.RunDetailRoute
import com.gowaist.app.ui.nav.RunEditRoute
import com.gowaist.app.ui.nav.RunImportRoute
import com.gowaist.app.ui.nav.RunTabRoute
import com.gowaist.app.ui.nav.SessionRoute
import com.gowaist.app.ui.nav.SessionSummaryRoute
import com.gowaist.app.ui.nav.SettingsRoute
import com.gowaist.app.ui.nav.TemplateEditRoute
import com.gowaist.app.ui.nav.TrainTabRoute
import com.gowaist.app.ui.plan.PlanCreateScreen
import com.gowaist.app.ui.plan.PlanScreen
import com.gowaist.app.ui.run.RunDetailScreen
import com.gowaist.app.ui.run.RunEditScreen
import com.gowaist.app.ui.run.RunImportScreen
import com.gowaist.app.ui.run.RunHubScreen
import com.gowaist.app.ui.run.RunLogScreen
import com.gowaist.app.ui.run.RunProgramScreen
import com.gowaist.app.ui.perf.PerformanceScreen
import com.gowaist.app.ui.perf.Vo2Screen
import com.gowaist.app.ui.nav.PerformanceRoute
import com.gowaist.app.ui.nav.RunLogRoute
import com.gowaist.app.ui.nav.RunProgramRoute
import com.gowaist.app.ui.nav.Vo2Route
import com.gowaist.app.ui.settings.SettingsScreen
import com.gowaist.app.ui.theme.Gw
import com.gowaist.app.ui.train.ChainEditScreen
import com.gowaist.app.ui.train.ExerciseDetailScreen
import com.gowaist.app.ui.train.ExerciseEditScreen
import com.gowaist.app.ui.train.IntervalTimerScreen
import com.gowaist.app.ui.train.SessionScreen
import com.gowaist.app.ui.train.SessionSummaryScreen
import com.gowaist.app.ui.train.TemplateEditScreen
import com.gowaist.app.ui.train.TrainScreen
import kotlin.reflect.KClass

val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

private data class Tab(val route: Any, val cls: KClass<*>, val label: Int, val icon: ImageVector, val color: @Composable () -> Color)

@Composable
fun AppNavigation(settings: AppSettings) {
    val nav = rememberNavController()
    val tabs = listOf(
        Tab(HomeRoute, HomeRoute::class, R.string.nav_home, Icons.Rounded.Home) { MaterialTheme.colorScheme.primary },
        Tab(RunTabRoute, RunTabRoute::class, R.string.nav_run, Icons.AutoMirrored.Rounded.DirectionsRun) { Gw.colors.run },
        Tab(TrainTabRoute, TrainTabRoute::class, R.string.nav_train, Icons.Rounded.FitnessCenter) { Gw.colors.train },
        Tab(PlanTabRoute, PlanTabRoute::class, R.string.nav_plan, Icons.Rounded.CalendarMonth) { Gw.colors.plan },
        Tab(GoalsTabRoute, GoalsTabRoute::class, R.string.nav_goals, Icons.Rounded.EmojiEvents) { Gw.colors.goal },
    )
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val onTab = tabs.any { t -> dest?.hasRoute(t.cls) == true }

    CompositionLocalProvider(LocalAppSettings provides settings) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (onTab) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                        tabs.forEach { t ->
                            val selected = dest?.hasRoute(t.cls) == true
                            val c = t.color()
                            NavigationBarItem(
                                selected = selected,
                                onClick = { nav.switchTab(t.route) },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(stringResource(t.label), fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    indicatorColor = c,
                                    selectedTextColor = c,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                NavHost(
                    navController = nav,
                    startDestination = HomeRoute,
                    enterTransition = { slideInHorizontally { it / 6 } + fadeIn() },
                    exitTransition = { fadeOut() },
                    popEnterTransition = { fadeIn() },
                    popExitTransition = { slideOutHorizontally { it / 6 } + fadeOut() },
                ) {
                    composable<HomeRoute>(enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) { HomeScreen(nav) }
                    composable<RunTabRoute>(enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) { RunHubScreen(nav) }
                    composable<TrainTabRoute>(enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) { TrainScreen(nav) }
                    composable<PlanTabRoute>(enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) { PlanScreen(nav) }
                    composable<GoalsTabRoute>(enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) { GoalsScreen(nav) }

                    composable<SettingsRoute> { SettingsScreen(nav) }
                    composable<BodyRoute> { BodyScreen(nav) }
                    composable<RunEditRoute> { RunEditScreen(nav) }
                    composable<RunImportRoute> { RunImportScreen(nav) }
                    composable<RunLogRoute> { RunLogScreen(nav) }
                    composable<RunProgramRoute> { RunProgramScreen(nav) }
                    composable<Vo2Route> { Vo2Screen(nav) }
                    composable<PerformanceRoute> { PerformanceScreen(nav) }
                    composable<RunDetailRoute> { RunDetailScreen(nav, it.toRoute<RunDetailRoute>().id) }
                    composable<ExerciseDetailRoute> { ExerciseDetailScreen(nav) }
                    composable<ExerciseEditRoute> { ExerciseEditScreen(nav) }
                    composable<ChainEditRoute> { ChainEditScreen(nav) }
                    composable<TemplateEditRoute> { TemplateEditScreen(nav) }
                    composable<SessionRoute> { SessionScreen(nav) }
                    composable<SessionSummaryRoute> { SessionSummaryScreen(nav) }
                    composable<IntervalTimerRoute> { IntervalTimerScreen(nav) }
                    composable<PlanCreateRoute> { PlanCreateScreen(nav) }
                    composable<GoalEditRoute> { GoalEditScreen(nav) }
                }
            }
        }
    }
}

fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
