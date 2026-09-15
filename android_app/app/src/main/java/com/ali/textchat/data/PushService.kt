package com.ali.textchat.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ali.textchat.MainActivity
import com.ali.textchat.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives FCM pushes (private messages + admin broadcasts) while the app is closed.
 * Dormant until google-services.json + the google-services Gradle plugin are added;
 * without them FirebaseApp is not initialised and this service is never invoked.
 */
class PushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Cached so the app can register it with the chat server on next connect.
        PushPrefs.saveToken(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val n = message.notification
        val title = n?.title ?: message.data["title"] ?: "رسالة جديدة"
        val body = n?.body ?: message.data["body"] ?: ""
        Push.ensureChannel(this)

        val intent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notif = NotificationCompat.Builder(this, Push.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(System.currentTimeMillis().toInt(), notif)
    }
}

/** Small holder for the FCM token before login. */
object PushPrefs {
    private const val PREFS = "ali_chat_push"
    private const val KEY = "fcm_token"
    fun saveToken(context: Context, token: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, token).apply()
    fun token(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
}

object Push {
    const val CHANNEL_ID = "chat"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "إشعارات الدردشة", NotificationManager.IMPORTANCE_HIGH)
                )
            }
        }
    }

    /**
     * Fetches the FCM token (if Firebase is configured) and hands it to [onToken].
     * Safe to call always — no-op when Firebase is not set up.
     */
    fun fetchToken(context: Context, onToken: (String) -> Unit) {
        ensureChannel(context)
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { t -> if (!t.isNullOrBlank()) { PushPrefs.saveToken(context, t); onToken(t) } }
        } catch (e: Exception) {
            // Firebase not initialised (no google-services.json) — push stays disabled.
        }
        PushPrefs.token(context)?.let(onToken)
    }
}
