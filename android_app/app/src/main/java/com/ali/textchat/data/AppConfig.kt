package com.ali.textchat.data

import android.os.Build

/**
 * Single place to point the app at the chat server.
 * Points to the live public Cloudflare Tunnel on physical devices,
 * or localhost:3001 when running in an Android emulator with adb reverse (CI).
 */
object AppConfig {
    const val DEFAULT_SERVER_URL = "https://habitat-liver-leg-travesti.trycloudflare.com"
    const val EMULATOR_SERVER_URL = "http://localhost:3001"

    fun defaultUrl(): String {
        val isEmulator = Build.FINGERPRINT.startsWith("generic")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
        return if (isEmulator) EMULATOR_SERVER_URL else DEFAULT_SERVER_URL
    }

    val SERVER_URL: String get() = defaultUrl()
}
