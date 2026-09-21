package `in`.marxen.pocket.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import `in`.marxen.pocket.ui.qr.PaymentSetupScreen
import `in`.marxen.pocket.ui.qr.PaymentsToConfirmScreen
import `in`.marxen.pocket.ui.qr.PaymentsToConfirmViewModel
import `in`.marxen.pocket.ui.qr.QRScannerScreen
import `in`.marxen.pocket.ui.settings.DefaultPaymentAppScreen
import `in`.marxen.pocket.ui.settings.SettingsScreen
import `in`.marxen.pocket.ui.settings.SettingsViewModel
import `in`.marxen.pocket.ui.splash.SplashScreen
import `in`.marxen.pocket.ui.welcome.WelcomeScreen
import `in`.marxen.pocket.ui.theme.PocketGreen
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
                CustomBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onScanClick = { navController.navigate(Routes.QR_SCANNER) },
                )
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
                    onScanQr = { navController.navigate(Routes.QR_SCANNER) },
                    onPaymentsToConfirm = { navController.navigate(Routes.PAYMENTS_TO_CONFIRM) },
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
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onDefaultPaymentApp = { navController.navigate(Routes.DEFAULT_PAYMENT_APP) },
                    onPaymentsToConfirm = { navController.navigate(Routes.PAYMENTS_TO_CONFIRM) },
                )
            }
            composable(Routes.QR_SCANNER) {
                QRScannerScreen(
                    onBack = { navController.popBackStack() },
                    onScanned = { rawUri ->
                        navController.navigate(Routes.paymentSetupRoute(rawUri)) {
                            popUpTo(Routes.QR_SCANNER) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = Routes.PAYMENT_SETUP,
                arguments = listOf(navArgument("scannedPaymentUri") { type = NavType.StringType }),
            ) { backStackEntry ->
                val uri = backStackEntry.arguments?.getString("scannedPaymentUri") ?: return@composable
                val decodedUri = java.net.URLDecoder.decode(uri, "UTF-8")
                PaymentSetupScreen(
                    scannedPaymentUri = decodedUri,
                    onBack = { navController.popBackStack() },
                    onPaymentLaunched = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    },
                    appContainer = appContainer,
                )
            }
            composable(Routes.PAYMENTS_TO_CONFIRM) {
                val vm: PaymentsToConfirmViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = PaymentsToConfirmViewModel.Factory(appContainer.repository),
                )
                PaymentsToConfirmScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = vm,
                )
            }
            composable(Routes.DEFAULT_PAYMENT_APP) {
                DefaultPaymentAppScreen(
                    onBack = { navController.popBackStack() },
                    prefs = appContainer.prefs,
                )
            }
        }
    }
}

@Composable
private fun CustomBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onScanClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .height(72.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left two items
            bottomNavItems.take(2).forEach { item ->
                val selected = currentRoute == item.route
                BottomNavItemView(
                    item = item,
                    selected = selected,
                    onClick = { onNavigate(item.route) },
                    modifier = Modifier.weight(1f),
                )
            }

            // Center spacer for the elevated scan button
            Spacer(modifier = Modifier.width(72.dp))

            // Right two items
            bottomNavItems.drop(2).forEach { item ->
                val selected = currentRoute == item.route
                BottomNavItemView(
                    item = item,
                    selected = selected,
                    onClick = { onNavigate(item.route) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Elevated centered scan button
        FloatingActionButton(
            onClick = onScanClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-20).dp)
                .size(60.dp)
                .shadow(8.dp, shape = CircleShape),
            shape = CircleShape,
            containerColor = PocketGreen,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp,
                pressedElevation = 12.dp,
            ),
            contentColor = Color.White,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_scan_qr),
                contentDescription = "Scan QR",
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun BottomNavItemView(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(72.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(item.icon),
            contentDescription = item.label,
            tint = if (selected) ActiveColor else InactiveColor,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) ActiveColor else InactiveColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
