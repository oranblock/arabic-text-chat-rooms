package com.ali.textchat.data

/**
 * Single place to point the app at the chat server.
 * Points to the live public Cloudflare Tunnel by default so that
 * any two physical devices anywhere in Iraq or worldwide connect immediately.
 */
object AppConfig {
    const val DEFAULT_SERVER_URL = "https://amongst-works-hamburg-lawrence.trycloudflare.com"
    const val LOCAL_SERVER_URL = "http://10.0.2.2:3001"

    const val SERVER_URL = DEFAULT_SERVER_URL
}
