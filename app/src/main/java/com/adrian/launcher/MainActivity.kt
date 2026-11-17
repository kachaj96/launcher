package com.adrian.launcher

import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Ink = Color(0xFFF2F2F2)
private val Quiet = Color(0xFFB4B4B4)
private val Accent = Color(0xFFFF8A3D)
private val Polish: Locale = Locale.forLanguageTag("pl-PL")

class MainActivity : ComponentActivity() {

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

        // A home screen has nowhere to go back to.
        onBackPressedDispatcher.addCallback(this) { }

        setContent {
            HomeStage0(
                isDefaultHome = isDefaultHome.value,
                onRequestDefault = ::requestDefaultHome,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDefaultState()
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

@Composable
private fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        value = LocalDateTime.now()
        // Wake up right after the next full minute.
        delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
    }
}

@Composable
private fun HomeStage0(isDefaultHome: Boolean, onRequestDefault: () -> Unit) {
    val now by rememberNow()
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Polish))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
    ) {
        Column(modifier = Modifier.padding(top = 64.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = now.hour.toString(),
                    color = Ink,
                    fontSize = 112.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 112.sp,
                )
                Text(
                    text = "%02d".format(now.minute),
                    color = Ink,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 8.dp, top = 14.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(text = date, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(text = "Etap 0: szkielet launchera", color = Quiet, fontSize = 14.sp)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (isDefaultHome) "Domyślny ekran główny: tak" else "Domyślny ekran główny: nie",
                color = if (isDefaultHome) Ink else Quiet,
                fontSize = 15.sp,
            )
            if (!isDefaultHome) {
                Text(
                    text = "Ustaw jako domyślny",
                    color = Ink,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .border(1.5.dp, Accent, RoundedCornerShape(22.dp))
                        .clickable(onClick = onRequestDefault)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}
