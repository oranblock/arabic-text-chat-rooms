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
                        var currentServerUrl by remember { mutableStateOf(Session.serverUrl(context)) }
                        val socket = remember(currentServerUrl) { ChatSocket(serverUrl = currentServerUrl, deviceHash = deviceHash) }

                        var loggedIn by remember { mutableStateOf(false) }
                        var busy by remember { mutableStateOf(false) }
                        var authError by remember { mutableStateOf<String?>(null) }

                        fun afterAuth(ok: Boolean, tokenOrError: String?) {
                            busy = false
                            if (ok) {
                                Session.saveToken(context, socket.token)
                                val last = Session.lastRoom(context)
                                socket.joinRoom(last)
                                socket.listRooms()
                                requestNotifPermissionIfNeeded()
                                Push.fetchToken(context) { fcm -> socket.registerPush(fcm) }
                                loggedIn = true
                            } else authError = tokenOrError
                        }

                        LaunchedEffect(currentServerUrl) {
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
                                serverUrl = currentServerUrl,
                                onUpdateServerUrl = { newUrl ->
                                    Session.saveServerUrl(context, newUrl)
                                    currentServerUrl = newUrl
                                },
                                onLogin = { n, p, ghost, lockP, muteN ->
                                    busy = true; authError = null
                                    socket.login(n, p, null, isGhost = ghost, lockPrivate = lockP, muteNotifications = muteN) { ok, r -> afterAuth(ok, r) }
                                },
                                onRegister = { n, p, displayName, age, gender, country, status, ghost, lockP, muteN ->
                                    busy = true; authError = null
                                    socket.register(
                                        name = n,
                                        password = p,
                                        age = age.toIntOrNull(),
                                        gender = gender,
                                        country = country.ifBlank { null },
                                        bio = status.ifBlank { null },
                                        isGhost = ghost,
                                        lockPrivate = lockP,
                                        muteNotifications = muteN
                                    ) { ok, r ->
                                        if (ok && displayName.isNotBlank()) {
                                            socket.updateProfile(null, null, null, null, null, null, null, displayName.trim()) { _, _ -> }
                                        }
                                        afterAuth(ok, r)
                                    }
                                },
                                onGuest = {
                                    busy = true; authError = null
                                    val guest = "زائر" + (1000..9999).random()
                                    socket.register(guest, "guest-" + System.currentTimeMillis(), guest = true) { ok, r -> afterAuth(ok, r) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
