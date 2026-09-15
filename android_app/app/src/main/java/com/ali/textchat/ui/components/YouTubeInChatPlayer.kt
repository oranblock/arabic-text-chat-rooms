package com.ali.textchat.ui.components

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * In-chat YouTube (Requirement 8): a compact 16:9 WebView playing the room's shared
 * video. When the room broadcasts a new videoId everyone's player switches to it.
 * Plays only while the app is open (YouTube/Play policy forbids background embed playback).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeInChatPlayer(
    videoId: String,
    videoTitle: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F172A))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("▶", color = Color.Red, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text(videoTitle, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Default.Close, "إغلاق", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        if (videoId.isNotBlank()) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(200.dp),
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
