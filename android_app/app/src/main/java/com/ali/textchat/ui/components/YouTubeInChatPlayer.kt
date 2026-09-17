package com.ali.textchat.ui.components

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * In-chat YouTube (Requirement 8): a compact 16:9 WebView playing the room's shared
 * video. When the room broadcasts a new videoId everyone's player switches to it.
 * Supports minimize/expand so members can chat freely while listening.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeInChatPlayer(
    videoId: String,
    videoTitle: String,
    startedBy: String = "",
    isWelcome: Boolean = false,
    startSeconds: Int = 0,
    onClose: () -> Unit,
    onVideoEnded: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isMinimized by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isWelcomeMode = isWelcome || startedBy == "فيديو ترحيبي" || startedBy == "ترحيب"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .border(1.dp, if (isWelcomeMode) Color(0xFF059669) else Color(0xFF334155), RoundedCornerShape(8.dp))
            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isMinimized = !isMinimized }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isWelcomeMode) Color(0xFF10B981) else Color(0xFFE50914))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(if (isWelcomeMode) "🎬 ترحيب" else "▶ يوتيوب", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = videoTitle.ifBlank { if (isWelcomeMode) "فيديو ترحيبي بالغرفة" else "مشغل يوتيوب الغرفة" },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (isWelcomeMode) {
                    Text(
                        text = "✨ فيديو ترحيبي رسمي للغرفة 🌟",
                        color = Color(0xFF34D399),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                } else if (startedBy.isNotBlank()) {
                    Text(
                        text = "بواسطة: $startedBy 👤",
                        color = Color(0xFF38BDF8),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = {
                    try {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://www.youtube.com/watch?v=$videoId")
                        )
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                },
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = "فتح في يوتيوب",
                    tint = Color.LightGray,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(2.dp))
            IconButton(
                onClick = { isMinimized = !isMinimized },
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = if (isMinimized) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = if (isMinimized) "تكبير" else "تصغير",
                    tint = Color.LightGray,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(2.dp))
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "إغلاق",
                    tint = Color.LightGray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // When minimized, do not render WebView to eliminate hardware acceleration surface smear
        if (!isMinimized && videoId.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(Color.Black)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            isClickable = true
                            isFocusable = true
                            isFocusableInTouchMode = true
                            setBackgroundColor(android.graphics.Color.BLACK)
                            val isEmu = android.os.Build.FINGERPRINT.startsWith("generic") || android.os.Build.HARDWARE.contains("goldfish") || android.os.Build.HARDWARE.contains("ranchu")
                            if (isEmu) {
                                setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                            }
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                loadsImagesAutomatically = true
                                userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun getDefaultVideoPoster(): android.graphics.Bitmap? {
                                    return android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                                }
                            }
                            webViewClient = object : android.webkit.WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                    val u = request?.url?.toString() ?: return false
                                    if (u.startsWith("ali-chat://video-ended")) {
                                        onVideoEnded(videoId)
                                        return true
                                    }
                                    if (u.contains("/player/")) {
                                        return false
                                    }
                                    // Block any navigation to full YouTube browser inside this WebView
                                    if (u.contains("youtube.com") || u.contains("youtu.be") || u.startsWith("http://") || u.startsWith("https://")) {
                                        try {
                                            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u)))
                                        } catch (_: Exception) {}
                                        return true // Never turn into a web browser
                                    }
                                    return true
                                }
                            }
                        }
                    },
                    update = { web ->
                        val tagKey = "$videoId:$startSeconds"
                        val currentTag = web.tag as? String
                        if (currentTag != tagKey) {
                            web.tag = tagKey
                            val remoteUrl = "${com.ali.textchat.data.AppConfig.defaultUrl()}/player/$videoId?start=$startSeconds"
                            web.loadUrl(remoteUrl)
                        }
                    },
                    onRelease = { web ->
                        web.stopLoading()
                        web.loadUrl("about:blank")
                        web.clearHistory()
                        web.removeAllViews()
                        web.destroy()
                    }
                )
            }
        }
    }
}
