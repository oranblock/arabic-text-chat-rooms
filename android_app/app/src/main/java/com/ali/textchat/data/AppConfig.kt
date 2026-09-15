package com.ali.textchat.data

/**
 * Single place to point the app at the chat server.
 * Change SERVER_URL to the deployed VPS (Hetzner/Contabo) address before release.
 */
object AppConfig {
    // Emulator -> host machine is 10.0.2.2; change to https://chat.yourdomain.com in production.
    const val SERVER_URL = "http://localhost:3001"
}
