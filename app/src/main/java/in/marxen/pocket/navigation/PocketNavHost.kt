package `in`.marxen.pocket.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import `in`.marxen.pocket.R
import `in`.marxen.pocket.data.local.AppContainer
import `in`.marxen.pocket.ui.calendar.CalendarScreen
import `in`.marxen.pocket.ui.expense.AddExpenseScreen
import `in`.marxen.pocket.ui.home.HomeScreen
import `in`.marxen.pocket.ui.insights.InsightsScreen
import `in`.marxen.pocket.ui.settings.SettingsScreen
import `in`.marxen.pocket.ui.settings.SettingsViewModel
import `in`.marxen.pocket.ui.splash.SplashScreen
import `in`.marxen.pocket.ui.welcome.WelcomeScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private data class BottomNavItem(val route: String, val label: String, val icon: Int)

private val bottomNavItems = listOf(
    BottomNavItem(Routes.HOME, "Home", R.drawable.ic_nav_home),
    BottomNavItem(Routes.CALENDAR, "Calendar", R.drawable.ic_nav_calendar),
    BottomNavItem(Routes.INSIGHTS, "Insights", R.drawable.ic_nav_insights),
    BottomNavItem(Routes.SETTINGS, "Settings", R.drawable.ic_nav_settings),
)

private val ActiveColor = Color(0xFF1A5C38)
private val InactiveColor = Color(0xFF999999)

@Composable
fun PocketNavHost(appContainer: AppContainer) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    val context = LocalContext.current
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(
            appContainer.prefs,
            appContainer.repository,
            appContainer.database,
            context,
        ),
    )

    val firstLaunchCompleted by appContainer.prefs.firstLaunchCompleted.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    painter = painterResource(item.icon),
                                    contentDescription = item.label,
                                    tint = if (selected) ActiveColor else InactiveColor,
                                )
                            },
                            label = { Text(item.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showBottomBar) {
                FloatingActionButton(
                    onClick = { navController.navigate(Routes.ADD) },
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Expense", tint = Color.White)
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(
                    onReady = {
                        val destination = if (firstLaunchCompleted == false) Routes.WELCOME else Routes.HOME
                        navController.navigate(destination) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.WELCOME) {
                WelcomeScreen(
                    onNameSaved = { name ->
                        val capitalizedName = name.split(" ").joinToString(" ") { word ->
                            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                        }
                        scope.launch {
                            appContainer.prefs.setUserName(capitalizedName)
                            appContainer.prefs.setFirstLaunchCompleted()
                        }
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.WELCOME) { inclusive = true }
                        }
                    },
                    onSkip = {
                        scope.launch {
                            appContainer.prefs.setFirstLaunchCompleted()
                        }
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.WELCOME) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onTransactionClick = { id -> navController.navigate(Routes.editRoute(id)) },
                    onAddExpense = { navController.navigate(Routes.ADD) },
                    onSeeAll = { navController.navigate(Routes.CALENDAR) },
                    onMonthClick = { /* Month picker handled inside HomeScreen */ },
                )
            }
            composable(Routes.CALENDAR) {
                CalendarScreen(
                    onTransactionClick = { id -> navController.navigate(Routes.editRoute(id)) },
                    onAddExpense = { navController.navigate(Routes.ADD) },
                )
            }
            composable(Routes.ADD) {
                AddExpenseScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.EDIT,
                arguments = listOf(navArgument("transactionId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val transactionId = backStackEntry.arguments?.getLong("transactionId")
                AddExpenseScreen(
                    transactionId = transactionId,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(Routes.INSIGHTS) { InsightsScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(viewModel = settingsViewModel)
            }
        }
    }
}
