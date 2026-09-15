package com.ali.textchat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.ali.textchat.data.ChatSocket
import com.ali.textchat.data.DeviceId
import com.ali.textchat.data.Push
import com.ali.textchat.data.Session
import com.ali.textchat.ui.screens.AuthScreen
import com.ali.textchat.ui.screens.ChatRoomScreen
import com.ali.textchat.ui.theme.ArabicTextChatTheme

class MainActivity : ComponentActivity() {
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op; push still works foreground */ }

    private fun requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ArabicTextChatTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val context = this@MainActivity
                        val deviceHash = remember { DeviceId.hash(context) }
                        val socket = remember { ChatSocket(deviceHash = deviceHash) }

                        var loggedIn by remember { mutableStateOf(false) }
                        var busy by remember { mutableStateOf(false) }
                        var authError by remember { mutableStateOf<String?>(null) }

                        fun afterAuth(ok: Boolean, tokenOrError: String?) {
                            busy = false
                            if (ok) {
                                Session.saveToken(context, socket.token)
                                socket.joinRoom("iraq")
                                socket.listRooms()
                                requestNotifPermissionIfNeeded()
                                Push.fetchToken(context) { fcm -> socket.registerPush(fcm) }
                                loggedIn = true
                            } else authError = tokenOrError
                        }

                        LaunchedEffect(Unit) {
                            val saved = Session.token(context)
                            if (saved != null) {
                                busy = true
                                socket.login(null, null, saved) { ok, res -> afterAuth(ok, res) }
                            }
                        }

                        if (loggedIn) {
                            ChatRoomScreen(
                                socket = socket,
                                onLogout = {
                                    Session.clear(context)
                                    socket.disconnect()
                                    loggedIn = false
                                }
                            )
                        } else {
                            AuthScreen(
                                busy = busy,
                                error = authError,
                                onLogin = { n, p -> busy = true; authError = null; socket.login(n, p, null) { ok, r -> afterAuth(ok, r) } },
                                onRegister = { n, p -> busy = true; authError = null; socket.register(n, p) { ok, r -> afterAuth(ok, r) } },
                                onGuest = {
                                    busy = true; authError = null
                                    val guest = "زائر" + (1000..9999).random()
                                    socket.register(guest, "guest-" + System.currentTimeMillis()) { ok, r -> afterAuth(ok, r) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
