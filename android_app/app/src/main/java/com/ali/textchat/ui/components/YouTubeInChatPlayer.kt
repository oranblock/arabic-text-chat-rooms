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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
            Spacer(Modifier.width(4.dp))
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

        AnimatedVisibility(visible = !isMinimized && videoId.isNotBlank()) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.domStorageEnabled = true
                        webChromeClient = WebChromeClient()
                    }
                },
                update = { web ->
                    val html = """
                        <html><body style="margin:0;background:#000">
                        <iframe width="100%" height="100%"
                          src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1&rel=0"
                          frameborder="0" allow="autoplay; encrypted-media" allowfullscreen></iframe>
                        </body></html>
                    """.trimIndent()
                    web.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
                }
            )
        }
    }
}
