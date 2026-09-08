package com.ali.textchat.security

import java.util.concurrent.ConcurrentHashMap

/**
 * محرك الحماية ومكافحة السبام والتكرار
 * يضمن منع الإغراق والتكرار وكتم الحسابات المشبوهة تلقائياً
 */
class AntiSpamEngine(
    private val minMessageIntervalMs: Long = 1200L, // الحد الأدنى بين كل رسالة وأخرى
    private val duplicateThresholdSeconds: Long = 30L // منع تكرار نفس الرسالة خلال 30 ثانية
) {
    private val lastMessageTimes = ConcurrentHashMap<String, Long>()
    private val lastMessageContents = ConcurrentHashMap<String, Pair<String, Long>>()

    sealed class ValidationResult {
        object Allowed : ValidationResult()
        data class RateLimited(val waitMs: Long) : ValidationResult()
        object DuplicateBlocked : ValidationResult()
        object AutoMuted : ValidationResult()
    }

    fun canSendMessage(userId: String, content: String, isMuted: Boolean): ValidationResult {
        if (isMuted) return ValidationResult.AutoMuted

        val now = System.currentTimeMillis()
        val lastTime = lastMessageTimes[userId] ?: 0L
        val diff = now - lastTime

        if (diff < minMessageIntervalMs) {
            return ValidationResult.RateLimited(minMessageIntervalMs - diff)
        }

        val lastContentPair = lastMessageContents[userId]
        if (lastContentPair != null) {
            val (lastText, timestamp) = lastContentPair
            if (lastText.trim().equals(content.trim(), ignoreCase = true) && 
                (now - timestamp) < (duplicateThresholdSeconds * 1000L)) {
                return ValidationResult.DuplicateBlocked
            }
        }

        lastMessageTimes[userId] = now
        lastMessageContents[userId] = Pair(content, now)
        return ValidationResult.Allowed
    }
}
