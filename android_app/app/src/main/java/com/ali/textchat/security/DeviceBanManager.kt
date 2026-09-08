package com.ali.textchat.security

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

/**
 * نظام حظر العتاد الصارم (Hardware UUID Ban)
 * يقوم بتوليد بصمة عتادية فريدة للجهاز مشفرة بـ SHA-256
 * لا يمكن تجاوزها بمسح البيانات أو تغيير الحساب أو استخدام برامج الـ VPN.
 */
class DeviceBanManager(private val context: Context) {

    private val bannedDeviceHashes = mutableSetOf<String>()

    @SuppressLint("HardwareIds")
    fun getDeviceHardwareFingerprint(): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "UNKNOWN_ID"

        val rawFingerprint = "${Build.BOARD}_${Build.BRAND}_${Build.DEVICE}_${Build.HARDWARE}_${Build.MANUFACTURER}_${Build.MODEL}_$androidId"
        return sha256(rawFingerprint)
    }

    fun isCurrentDeviceBanned(): Boolean {
        val currentHash = getDeviceHardwareFingerprint()
        return bannedDeviceHashes.contains(currentHash)
    }

    fun banDeviceHash(deviceHash: String) {
        bannedDeviceHashes.add(deviceHash)
    }

    fun unbanDeviceHash(deviceHash: String) {
        bannedDeviceHashes.remove(deviceHash)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
