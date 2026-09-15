package com.ali.textchat.data

import android.content.Context

/** Persists the login token so a returning user skips the auth screen. */
object Session {
    private const val PREFS = "ali_chat_session"
    private const val KEY_TOKEN = "token"

    fun saveToken(context: Context, token: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .apply { if (token == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, token) }
            .apply()
    }

    fun token(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)

    fun clear(context: Context) = saveToken(context, null)
}
