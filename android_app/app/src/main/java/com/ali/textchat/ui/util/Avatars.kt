package com.ali.textchat.ui.util

import android.content.Context
import coil.ImageLoader
import coil.decode.SvgDecoder

/** dicebear and BoomChat avatars are often SVG; Coil needs the SVG decoder registered. */
fun svgCapableLoader(context: Context): ImageLoader =
    ImageLoader.Builder(context)
        .components { add(SvgDecoder.Factory()) }
        .crossfade(true)
        .build()

/** Stable bright color from a name, for the letter-avatar fallback. */
fun colorForName(name: String): Long {
    val palette = listOf(
        0xFF2563EB, 0xFF059669, 0xFFD97706, 0xFFDC2626,
        0xFF7C3AED, 0xFF0891B2, 0xFFDB2777, 0xFF65A30D
    )
    var h = 0
    for (c in name) h = h * 31 + c.code
    return palette[(h and 0x7fffffff) % palette.size]
}
