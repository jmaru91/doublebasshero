package com.doublebasshero.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        vm.handleIntent(intent)
        setContent {
            DbhTheme {
                val live by vm.live.collectAsState()
                LaunchedEffect(live != null && live?.outcome == null) {
                    // Keep the screen on while a session is running in the foreground.
                    if (live != null && live?.outcome == null) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                App(vm, requestNotifications = ::requestNotifications)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
fun App(vm: AppViewModel, requestNotifications: () -> Unit) {
    val screen by vm.screen.collectAsState()
    val ready by vm.ready.collectAsState()
    val live by vm.live.collectAsState()
    val data by vm.data.collectAsState()
    Box(Modifier.fillMaxSize().background(C.night).statusBarsPadding().navigationBarsPadding()) {
        if (!ready) return@Box
        // A live session always wins: the player is where it belongs.
        val current = if (live != null && screen != Screen.Onboarding || live?.placement == true) Screen.Player else screen
        if (current != Screen.Onboarding && current != Screen.Player && data == null) {
            Box(Modifier.fillMaxSize())
            return@Box
        }
        val tabs = listOf(Screen.Today, Screen.Calendar, Screen.Progress, Screen.Levels)
        BackHandler(enabled = current != Screen.Today && current != Screen.Player && current != Screen.Onboarding) { vm.go(Screen.Today) }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (current) {
                    Screen.Onboarding -> OnboardingScreen(vm, requestNotifications)
                    Screen.Today -> TodayScreen(vm)
                    Screen.Calendar -> CalendarScreen(vm)
                    Screen.Progress -> ProgressScreen(vm)
                    Screen.Levels -> LevelsScreen(vm)
                    Screen.Settings -> SettingsScreen(vm)
                    Screen.Review -> ReviewScreen(vm)
                    Screen.EditWeek -> EditWeekScreen(vm)
                    Screen.Player -> PlayerScreen(vm)
                    is Screen.LevelUp -> LevelUpScreen(vm, current)
                }
            }
            if (current in tabs) {
                Row(
                    Modifier.fillMaxWidth().background(C.navBar).padding(top = 6.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    NavItem("Today", Icons.home, current == Screen.Today) { vm.go(Screen.Today) }
                    NavItem("Calendar", Icons.calendar, current == Screen.Calendar) { vm.go(Screen.Calendar) }
                    NavItem("Progress", Icons.chart, current == Screen.Progress) { vm.go(Screen.Progress) }
                    NavItem("Levels", Icons.layers, current == Screen.Levels) { vm.go(Screen.Levels) }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) C.speed else C.dim
    Column(
        Modifier.weight(1f).clickable(role = Role.Tab, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
