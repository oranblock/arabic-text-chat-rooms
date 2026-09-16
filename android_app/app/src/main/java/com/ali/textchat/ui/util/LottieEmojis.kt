package com.ali.textchat.ui.util

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition

data class LottieEmojiItem(
    val code: String,
    val nameAr: String,
    val fallbackEmoji: String,
    val aliases: List<String> = emptyList()
)

object LottieEmojis {
    val items = listOf(
        LottieEmojiItem("fire", "نار", "🔥", listOf("نار")),
        LottieEmojiItem("heart", "قلب", "❤️", listOf("قلب", "حب")),
        LottieEmojiItem("laugh", "ضحكة", "😂", listOf("ضحك", "ضحكة")),
        LottieEmojiItem("rofl", "ميت ضحك", "🤣", listOf("متت", "كركرة")),
        LottieEmojiItem("party", "احتفال", "🎉", listOf("حفلة", "مبروك")),
        LottieEmojiItem("love", "عيون حب", "😍", listOf("عشق", "غرام")),
        LottieEmojiItem("clap", "تصفيق", "👏", listOf("كفو", "صفقة")),
        LottieEmojiItem("thumbs_up", "إعجاب", "👍", listOf("لايك", "تمام")),
        LottieEmojiItem("rose", "وردة جورية", "🌹", listOf("وردة", "جوري")),
        LottieEmojiItem("star", "نجمة", "⭐", listOf("نجمة", "مميز")),
        LottieEmojiItem("cool", "كشخة", "😎", listOf("كشخة", "هيبة")),
        LottieEmojiItem("kiss", "بوسة", "😘", listOf("بوسة", "قبلة")),
        LottieEmojiItem("blush", "خجلان", "😊", listOf("خجل", "ابتسامة")),
        LottieEmojiItem("cry", "دموع", "😭", listOf("بجي", "بكاء")),
        LottieEmojiItem("pray", "دعاء", "🙏", listOf("دعاء", "يارب")),
        LottieEmojiItem("hundred", "100%", "💯", listOf("مية", "كامل"))
    )

    private val codeToItem: Map<String, LottieEmojiItem> by lazy {
        val map = mutableMapOf<String, LottieEmojiItem>()
        for (item in items) {
            map[item.code.lowercase()] = item
            for (alias in item.aliases) {
                map[alias.lowercase()] = item
            }
        }
        map
    }

    fun isLottieCode(rawCode: String): Boolean = codeToItem.containsKey(rawCode.lowercase().trim())

    fun resolveItem(rawCode: String): LottieEmojiItem? = codeToItem[rawCode.lowercase().trim()]

    fun assetPath(code: String): String = "lottie_emojis/$code.json"
}

@Composable
fun LottieEmojiView(
    code: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    onClick: (() -> Unit)? = null
) {
    val item = LottieEmojis.resolveItem(code)
    val canonicalCode = item?.code ?: code
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("lottie_emojis/$canonicalCode.json")
    )

    val mod = modifier
        .size(size)
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)

    LottieAnimation(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        modifier = mod
    )
}
