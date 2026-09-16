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
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMinimized by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .shadow(4.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
            .background(Color(0xFF0F172A))
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
                    .background(Color(0xFFE50914))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("▶ يوتيوب", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = videoTitle.ifBlank { "مشغل يوتيوب الغرفة" },
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isMinimized) 0.dp else 200.dp)
        ) {
            if (videoId.isNotBlank()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                loadsImagesAutomatically = true
                                userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                            }
                            webChromeClient = WebChromeClient()
                            webViewClient = object : android.webkit.WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                    val u = request?.url?.toString() ?: return false
                                    if (u.contains("youtube.com/embed")) return false
                                    return try {
                                        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u)))
                                        true
                                    } catch (_: Exception) { false }
                                }
                            }
                        }
                    },
                    update = { web ->
                        val targetUrl = "https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&enablejsapi=1&rel=0"
                        val currentId = web.tag as? String
                        if (currentId != videoId) {
                            web.tag = videoId
                            web.loadUrl(targetUrl)
                        }
                    }
                )
            }
        }
    }
}
