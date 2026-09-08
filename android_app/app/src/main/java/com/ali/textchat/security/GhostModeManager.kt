package com.ali.textchat.security

import com.ali.textchat.model.ChatMessage
import java.util.concurrent.ConcurrentHashMap

/**
 * مدير وضع الشبح (Ghost Mode)
 * يتيح للإدارة إخضاع المخربين لوضع الشبح بحيث تظهر الرسائل لهم وحدهم
 * دون أن يراها باقي أعضاء الغرفة إطلاقاً.
 */
class GhostModeManager {
    private val ghostedUsers = ConcurrentHashMap.newKeySet<String>()

    fun setGhostMode(userId: String, enabled: Boolean) {
        if (enabled) {
            ghostedUsers.add(userId)
        } else {
            ghostedUsers.remove(userId)
        }
    }

    fun isUserGhost(userId: String): Boolean = ghostedUsers.contains(userId)

    /**
     * فلترة الرسائل للتسليم:
     * إذا كان المرسل شبحاً، يُسمح بتسليمها فقط لنفس المرسل
     */
    fun shouldDeliverMessage(message: ChatMessage, recipientUserId: String): Boolean {
        if (message.isGhost || isUserGhost(message.senderId)) {
            return message.senderId == recipientUserId
        }
        return true
    }
}
