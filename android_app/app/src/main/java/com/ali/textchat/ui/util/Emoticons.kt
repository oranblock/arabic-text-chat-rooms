package com.ali.textchat.ui.util

import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder

/**
 * BoomChat-style emoticons. Drop animated GIFs into app/src/main/assets/emoticons/
 * named "<code>.gif"; they show in the picker and any ":<code>:" token in a message
 * renders as the GIF. A few samples ship so the feature is visible out of the box.
 */
object Emoticons {
    private var codes: List<String>? = null

    fun codes(context: Context): List<String> {
        codes?.let { return it }
        val list = try {
            (context.assets.list("emoticons") ?: emptyArray())
                .filter { it.endsWith(".gif", true) }
                .map { it.removeSuffix(".gif") }
                .sorted()
        } catch (e: Exception) {
            emptyList()
        }
        codes = list
        return list
    }

    fun assetUri(code: String) = "file:///android_asset/emoticons/$code.gif"

    /** Splits text into plain runs, Lottie vector animations, and ":code:" emoticon tokens. */
    fun tokenize(text: String, known: Set<String>): List<Token> {
        val out = mutableListOf<Token>()
        val regex = Regex(":([A-Za-z0-9_\\u0600-\\u06FF]+):")
        var last = 0
        for (m in regex.findAll(text)) {
            if (m.range.first > last) out.add(Token.Text(text.substring(last, m.range.first)))
            val rawCode = m.groupValues[1]
            if (LottieEmojis.isLottieCode(rawCode)) {
                val item = LottieEmojis.resolveItem(rawCode)
                out.add(Token.Lottie(item?.code ?: rawCode))
            } else if (rawCode in known) {
                out.add(Token.Emoticon(rawCode))
            } else {
                out.add(Token.Text(m.value))
            }
            last = m.range.last + 1
        }
        if (last < text.length) out.add(Token.Text(text.substring(last)))
        return out
    }

    sealed class Token {
        data class Text(val value: String) : Token()
        data class Emoticon(val code: String) : Token()
        data class Lottie(val code: String) : Token()
    }
}

/** Coil loader that decodes animated GIFs (and SVG avatars). */
fun gifCapableLoader(context: Context): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            add(SvgDecoder.Factory())
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
            else add(GifDecoder.Factory())
        }
        .build()
