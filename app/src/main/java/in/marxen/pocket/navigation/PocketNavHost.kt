package `in`.marxen.pocket.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
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
import `in`.marxen.pocket.ui.insights.CategoryDetailScreen
import `in`.marxen.pocket.ui.insights.CategoryListScreen
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

private val noEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { EnterTransition.None }
private val noExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { ExitTransition.None }

@Composable
private fun PocketBottomBar(
    currentRoute: String?,
    onTabClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White),
    ) {
        HorizontalDivider(color = InactiveColor.copy(alpha = 0.3f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            bottomNavItems.forEach { item ->
                val selected = currentRoute == item.route
                val interactionSource = remember { MutableInteractionSource() }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) { onTabClick(item.route) }
                        .padding(vertical = 4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (selected) ActiveColor.copy(alpha = 0.12f)
                                else Color.Transparent,
                            )
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(item.icon),
                            contentDescription = item.label,
                            tint = if (selected) ActiveColor else InactiveColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) ActiveColor else InactiveColor,
                    )
                }
            }
        }
    }
}

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
                PocketBottomBar(
                    currentRoute = currentRoute,
                    onTabClick = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
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
            enterTransition = { fadeIn(animationSpec = tween(150)) },
            exitTransition = { fadeOut(animationSpec = tween(150)) },
            popEnterTransition = { fadeIn(animationSpec = tween(150)) },
            popExitTransition = { fadeOut(animationSpec = tween(150)) },
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
                            word.lowercase().replaceFirstChar { it.titlecase() }
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
            composable(
                route = Routes.HOME,
                enterTransition = noEnter,
                exitTransition = noExit,
                popEnterTransition = noEnter,
                popExitTransition = noExit,
            ) {
                HomeScreen(
                    onTransactionClick = { id -> navController.navigate(Routes.editRoute(id)) },
                    onAddExpense = { navController.navigate(Routes.ADD) },
                    onSeeAll = { navController.navigate(Routes.CALENDAR) },
                    onMonthClick = { /* Month picker handled inside HomeScreen */ },
                )
            }
            composable(
                route = Routes.CALENDAR,
                enterTransition = noEnter,
                exitTransition = noExit,
                popEnterTransition = noEnter,
                popExitTransition = noExit,
            ) {
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
            composable(
                route = Routes.INSIGHTS,
                enterTransition = noEnter,
                exitTransition = noExit,
                popEnterTransition = noEnter,
                popExitTransition = noExit,
            ) {
                InsightsScreen(
                    onSeeAllCategories = { month -> navController.navigate(Routes.categoryListRoute(month)) },
                )
            }
            composable(
                route = Routes.CATEGORY_LIST,
                arguments = listOf(navArgument("month") { type = NavType.StringType; defaultValue = java.time.YearMonth.now().toString() }),
            ) { backStackEntry ->
                val month = backStackEntry.arguments?.getString("month")?.let {
                    java.time.YearMonth.parse(it)
                } ?: java.time.YearMonth.now()
                CategoryListScreen(
                    month = month,
                    onCategoryClick = { categoryId, categoryName ->
                        navController.navigate(Routes.categoryDetailRoute(categoryId, categoryName, month))
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.CATEGORY_DETAIL,
                arguments = listOf(
                    navArgument("categoryId") { type = NavType.LongType },
                    navArgument("name") { type = NavType.StringType; defaultValue = "Category" },
                    navArgument("month") { type = NavType.StringType; defaultValue = java.time.YearMonth.now().toString() },
                ),
            ) { backStackEntry ->
                val categoryId = backStackEntry.arguments?.getLong("categoryId") ?: 0L
                val categoryName = backStackEntry.arguments?.getString("name") ?: "Category"
                val month = backStackEntry.arguments?.getString("month")?.let {
                    java.time.YearMonth.parse(it)
                } ?: java.time.YearMonth.now()
                CategoryDetailScreen(
                    categoryId = categoryId,
                    categoryName = categoryName,
                    month = month,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.SETTINGS,
                enterTransition = noEnter,
                exitTransition = noExit,
                popEnterTransition = noEnter,
                popExitTransition = noExit,
            ) {
                SettingsScreen(viewModel = settingsViewModel)
            }
        }
    }
}
