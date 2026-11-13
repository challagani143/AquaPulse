package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.data.HydrationRepository
import com.example.notifications.NotificationHelper
import com.example.notifications.ReminderScheduler
import com.example.ui.HydrationUiState
import com.example.ui.HydrationViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.RemindersScreen
import com.example.ui.theme.AquaPulseTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_TODAY = "today"
const val ROUTE_INSIGHTS = "insights"
const val ROUTE_REMINDERS = "reminders"

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    NotificationHelper.ensureChannel(this)
    enableEdgeToEdge()
    setContent {
      AquaPulseTheme {
        AppNavigation()
      }
    }
  }
}

private data class NavTab(
  val route: String,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

private val navTabs = listOf(
  NavTab(
    route = ROUTE_TODAY,
    labelRes = R.string.nav_today,
    selectedIcon = Icons.Rounded.WaterDrop,
    unselectedIcon = Icons.Outlined.WaterDrop,
    testTag = "nav_tab_today",
  ),
  NavTab(
    route = ROUTE_INSIGHTS,
    labelRes = R.string.nav_insights,
    selectedIcon = Icons.Rounded.BarChart,
    unselectedIcon = Icons.Outlined.BarChart,
    testTag = "nav_tab_insights",
  ),
  NavTab(
    route = ROUTE_REMINDERS,
    labelRes = R.string.nav_reminders,
    selectedIcon = Icons.Rounded.NotificationsActive,
    unselectedIcon = Icons.Outlined.Notifications,
    testTag = "nav_tab_reminders",
  ),
)

@Composable
fun AppNavigation() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  var isCheckingAutoSignIn by remember { mutableStateOf(Firebase.auth.currentUser == null) }

  LaunchedEffect(Unit) {
    if (Firebase.auth.currentUser == null) {
      attemptAutoSignIn(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = { isCheckingAutoSignIn = false },
        onUnauthenticated = { isCheckingAutoSignIn = false },
        scope = scope,
      )
    } else {
      isCheckingAutoSignIn = false
    }
  }

  val currentUser by
    Firebase.auth.authStateFlow().collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
  val user = currentUser

  if (isCheckingAutoSignIn && user == null) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
      }
    }
  } else if (user == null) {
    AuthScreen(onAuthSuccess = { isCheckingAutoSignIn = false })
  } else {
    key(user.uid) {
      AquaPulseMainScreen(currentUserId = user.uid)
    }
  }
}

@Composable
fun AquaPulseMainScreen(currentUserId: String) {
  val context = LocalContext.current
  val viewModel: HydrationViewModel =
    viewModel(
      key = currentUserId,
      factory = viewModelFactory {
        initializer {
          val app = checkNotNull(this[APPLICATION_KEY]) { "APPLICATION_KEY missing" }
          val databaseId = app.getString(R.string.firestore_database_id)
          val db = FirebaseFirestore.getInstance(databaseId)
          HydrationViewModel(HydrationRepository(db), currentUserId)
        }
      },
    )

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
  val isMutating by viewModel.isMutating.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  var localToastMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(statusMessage) {
    val msg = statusMessage
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearStatusMessage()
    }
  }

  LaunchedEffect(localToastMessage) {
    val msg = localToastMessage
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      localToastMessage = null
    }
  }

  // Sync local AlarmManager reminder schedule whenever profile updates
  LaunchedEffect(uiState) {
    val state = uiState
    if (state is HydrationUiState.Success) {
      val profile = state.data.profile
      ReminderScheduler.syncSchedule(
        context = context,
        enabled = profile.notificationsEnabled,
        intervalMinutes = profile.reminderIntervalMinutes,
        wakeHour = profile.wakeHour.toInt(),
        sleepHour = profile.sleepHour.toInt(),
        dailyGoalMl = profile.dailyGoalMl,
      )
    }
  }

  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route ?: ROUTE_TODAY

  fun navigateToTab(route: String) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 600.dp

    Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      bottomBar = {
        if (!useNavRail) {
          NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.navigationBarsPadding(),
          ) {
            navTabs.forEach { tab ->
              val selected = currentRoute == tab.route
              val label = stringResource(tab.labelRes)
              NavigationBarItem(
                selected = selected,
                onClick = { navigateToTab(tab.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = label,
                  )
                },
                label = { Text(label) },
                modifier = Modifier.testTag(tab.testTag),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useNavRail) {
          NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxHeight().statusBarsPadding(),
          ) {
            Spacer(modifier = Modifier.height(16.dp))
            navTabs.forEach { tab ->
              val selected = currentRoute == tab.route
              val label = stringResource(tab.labelRes)
              NavigationRailItem(
                selected = selected,
                onClick = { navigateToTab(tab.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = label,
                  )
                },
                label = { Text(label) },
                modifier = Modifier.testTag(tab.testTag),
              )
            }
          }
        }

        when (val state = uiState) {
          is HydrationUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
              ) {
                CircularProgressIndicator(
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(40.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                  text = "Syncing your hydration...",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          is HydrationUiState.Error -> {
            Box(
              modifier = Modifier.fillMaxSize().padding(24.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = state.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
              )
            }
          }

          is HydrationUiState.Success -> {
            NavHost(
              navController = navController,
              startDestination = ROUTE_TODAY,
              modifier = Modifier.fillMaxSize(),
            ) {
              composable(ROUTE_TODAY) {
                DashboardScreen(
                  data = state.data,
                  isMutating = isMutating,
                  onLogDrink = { amount, type, note ->
                    viewModel.logDrink(amount, type, note)
                  },
                  onDeleteLog = { logId -> viewModel.deleteLog(logId) },
                  onNavigateToReminders = { navigateToTab(ROUTE_REMINDERS) },
                )
              }
              composable(ROUTE_INSIGHTS) {
                HistoryScreen(data = state.data)
              }
              composable(ROUTE_REMINDERS) {
                RemindersScreen(
                  data = state.data,
                  onSaveProfile = { goal, unit, enabled, interval, wake, sleep, msg ->
                    viewModel.updateProfile(
                      dailyGoalMl = goal,
                      preferredUnit = unit,
                      notificationsEnabled = enabled,
                      reminderIntervalMinutes = interval,
                      wakeHour = wake,
                      sleepHour = sleep,
                      feedbackMessage = msg,
                    )
                  },
                  onTestNotificationSent = { msg -> localToastMessage = msg },
                )
              }
            }
          }
        }
      }
    }
  }
}
