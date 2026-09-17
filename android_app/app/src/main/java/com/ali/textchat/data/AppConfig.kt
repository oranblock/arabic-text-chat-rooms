package com.ali.textchat.data

import android.os.Build

/**
 * Single place to point the app at the chat server.
 * Points to the live public Cloudflare Tunnel on physical devices,
 * or localhost:3001 when running in an Android emulator with adb reverse (CI).
 */
object AppConfig {
    const val DEFAULT_SERVER_URL = "http://192.236.249.134:3000"
    const val EMULATOR_SERVER_URL = "http://192.236.249.134:3000"

    fun defaultUrl(): String = DEFAULT_SERVER_URL

    val SERVER_URL: String get() = defaultUrl()
}
