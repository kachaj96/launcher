package com.adrian.launcher

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.adrian.launcher.data.AppEntry
import com.adrian.launcher.data.AppRepository
import com.adrian.launcher.drawer.AppDrawer
import com.adrian.launcher.home.HomeScreen
import com.adrian.launcher.ui.DefaultAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var repository: AppRepository
    private var appsCallback: LauncherApps.Callback? = null

    private val apps = mutableStateOf<List<AppEntry>>(emptyList())
    private val drawerOpen = mutableStateOf(false)
    private val isDefaultHome = mutableStateOf(false)

    private val roleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshDefaultState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        repository = AppRepository(this)
        appsCallback = repository.registerChanges { reloadApps() }
        reloadApps()

        // A home screen has nowhere to go back to; the drawer adds its own handler on top.
        onBackPressedDispatcher.addCallback(this) { }

        setContent {
            val accent = DefaultAccent
            BackHandler(enabled = drawerOpen.value) { drawerOpen.value = false }

            Box(Modifier.fillMaxSize()) {
                HomeScreen(
                    accent = accent,
                    isDefaultHome = isDefaultHome.value,
                    onRequestDefault = ::requestDefaultHome,
                    onOpenDrawer = { drawerOpen.value = true },
                )
                AnimatedVisibility(
                    visible = drawerOpen.value,
                    enter = slideInVertically { it / 6 } + fadeIn(),
                    exit = slideOutVertically { it / 6 } + fadeOut(),
                ) {
                    AppDrawer(
                        apps = apps.value,
                        accent = accent,
                        onLaunch = ::launchApp,
                        onClose = { drawerOpen.value = false },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already on the launcher: go back to the home screen.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            drawerOpen.value = false
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDefaultState()
    }

    override fun onDestroy() {
        appsCallback?.let { repository.unregisterChanges(it) }
        super.onDestroy()
    }

    private fun reloadApps() {
        lifecycleScope.launch {
            apps.value = withContext(Dispatchers.Default) { repository.loadApps() }
        }
    }

    private fun launchApp(app: AppEntry) {
        runCatching { repository.launch(app) }
        drawerOpen.value = false
    }

    private fun refreshDefaultState() {
        val roleManager = getSystemService(RoleManager::class.java)
        isDefaultHome.value = roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true
    }

    private fun requestDefaultHome() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            roleRequest.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
        } else {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }
}
