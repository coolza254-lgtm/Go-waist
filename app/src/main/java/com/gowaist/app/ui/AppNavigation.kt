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
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Home
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
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
import com.gowaist.app.ui.nav.GoalEditRoute
import com.gowaist.app.ui.nav.GoalsRoute
import com.gowaist.app.ui.nav.HistoryRoute
import com.gowaist.app.ui.nav.HomeRoute
import com.gowaist.app.ui.nav.PerformanceRoute
import com.gowaist.app.ui.nav.ProgressRoute
import com.gowaist.app.ui.nav.RunDetailRoute
import com.gowaist.app.ui.nav.RunEditRoute
import com.gowaist.app.ui.nav.RunImportRoute
import com.gowaist.app.ui.nav.SettingsRoute
import com.gowaist.app.ui.nav.ShoeEditRoute
import com.gowaist.app.ui.nav.ShoesRoute
import com.gowaist.app.ui.nav.Vo2Route
import com.gowaist.app.ui.perf.PerformanceScreen
import com.gowaist.app.ui.perf.ProgressScreen
import com.gowaist.app.ui.perf.Vo2Screen
import com.gowaist.app.ui.run.HistoryScreen
import com.gowaist.app.ui.run.RunDetailScreen
import com.gowaist.app.ui.run.RunEditScreen
import com.gowaist.app.ui.run.RunImportScreen
import com.gowaist.app.ui.settings.SettingsScreen
import com.gowaist.app.ui.shoes.ShoeEditScreen
import com.gowaist.app.ui.shoes.ShoesScreen
import com.gowaist.app.ui.theme.Gw
import kotlin.reflect.KClass

val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

private data class Tab(val route: Any, val cls: KClass<*>, val label: Int, val icon: ImageVector)

@Composable
fun AppNavigation(settings: AppSettings) {
    val nav = rememberNavController()
    val tabs = listOf(
        Tab(HomeRoute, HomeRoute::class, R.string.nav_home, Icons.Rounded.Home),
        Tab(HistoryRoute, HistoryRoute::class, R.string.nav_history, Icons.AutoMirrored.Rounded.List),
        Tab(ProgressRoute, ProgressRoute::class, R.string.nav_progress, Icons.AutoMirrored.Rounded.ShowChart),
        Tab(ShoesRoute, ShoesRoute::class, R.string.nav_shoes, Icons.Rounded.Hiking),
    )
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val onTab = tabs.any { t -> dest?.hasRoute(t.cls) == true }

    CompositionLocalProvider(LocalAppSettings provides settings) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (onTab) {
                    Column {
                        HorizontalDivider(thickness = 1.5.dp, color = Gw.colors.ink)
                        NavigationBar(containerColor = Gw.colors.card, tonalElevation = 0.dp) {
                            tabs.forEach { t ->
                                val selected = dest?.hasRoute(t.cls) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { nav.switchTab(t.route) },
                                    icon = { Icon(t.icon, contentDescription = null) },
                                    label = { Text(stringResource(t.label), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = Gw.colors.muted,
                                        unselectedTextColor = Gw.colors.muted,
                                    ),
                                )
                            }
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
                    composable<HistoryRoute>(enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) { HistoryScreen(nav) }
                    composable<ProgressRoute>(enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) { ProgressScreen(nav) }
                    composable<ShoesRoute>(enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) { ShoesScreen(nav) }

                    composable<SettingsRoute> { SettingsScreen(nav) }
                    composable<BodyRoute> { BodyScreen(nav) }
                    composable<Vo2Route> { Vo2Screen(nav) }
                    composable<PerformanceRoute> { PerformanceScreen(nav) }
                    composable<GoalsRoute> { GoalsScreen(nav) }
                    composable<GoalEditRoute> { GoalEditScreen(nav) }
                    composable<RunEditRoute> { RunEditScreen(nav) }
                    composable<RunImportRoute> { RunImportScreen(nav) }
                    composable<RunDetailRoute> { RunDetailScreen(nav, it.toRoute<RunDetailRoute>().id) }
                    composable<ShoeEditRoute> { ShoeEditScreen(nav) }
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
