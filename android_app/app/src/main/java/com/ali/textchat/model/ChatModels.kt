package com.ali.textchat.model

import kotlinx.serialization.Serializable

@Serializable
enum class UserRank(val titleAr: String, val badge: String) {
    OWNER("صاحب التطبيق", "👑"),
    MODERATOR("مشرف عام", "🛡️"),
    VIP_DIAMOND("عضو مميز", "💎"),
    REGULAR("عضو متفاعل", "👤"),
    BOT("بوت", "🤖")
}

@Serializable
data class ChatUser(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val rank: UserRank = UserRank.REGULAR,
    val customHexColor: String? = null,
    val deviceId: String,
    val isMuted: Boolean = false,
    val isGhost: Boolean = false
)

@Serializable
data class ChatMessage(
    val id: String,
    val roomId: String,
    val senderId: String,
    val senderName: String,
    val senderAvatar: String,
    val senderRank: UserRank,
    val customHexColor: String? = null,
    val text: String,
    val timestamp: String,
    val isGhost: Boolean = false
)

/** A private-message inbox entry (someone this user has a thread with). */
data class PmThread(
    val userId: String,
    val name: String,
    val rank: UserRank = UserRank.REGULAR,
    val avatarUrl: String = "",
    val lastText: String = ""
)

@Serializable
data class ChatRoom(
    val id: String,
    val title: String,
    val description: String,
    val topic: String = "",
    val onlineCount: Int = 0,
    val isLocked: Boolean = false,
    val currentYoutubeUrl: String? = null
)
