package com.ali.textchat.data

import android.content.Context

/** Persists the login token and server URL so settings survive across app launches. */
object Session {
    private const val PREFS = "ali_chat_session"
    private const val KEY_TOKEN = "token"
    private const val KEY_SERVER_URL = "server_url"

    fun saveToken(context: Context, token: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .apply { if (token == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, token) }
            .apply()
    }

    fun token(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)

    fun saveServerUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SERVER_URL, url.trim().trimEnd('/'))
            .apply()
    }

    fun serverUrl(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SERVER_URL, null)
        return if (!saved.isNullOrBlank()) saved else AppConfig.defaultUrl()
    }

    fun clear(context: Context) = saveToken(context, null)
}
