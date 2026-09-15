package com.ali.textchat.data

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

/**
 * Stable per-install identifier used for the server's hardware ban (Requirement: Hardware Ban).
 * ANDROID_ID is per app-signing-key + device, so a banned device stays banned across new accounts,
 * while remaining hashed so the raw id never leaves the phone.
 */
object DeviceId {
    @SuppressLint("HardwareIds")
    fun hash(context: Context): String {
        val raw = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
