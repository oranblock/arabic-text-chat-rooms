package com.ali.textchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.ali.textchat.data.AppConfig
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ali.textchat.model.ChatMessage
import com.ali.textchat.model.UserRank
import com.ali.textchat.ui.theme.*
import com.ali.textchat.ui.util.colorForName
import com.ali.textchat.ui.util.svgCapableLoader
import com.ali.textchat.ui.util.Emoticons
import com.ali.textchat.ui.util.gifCapableLoader
import androidx.compose.foundation.layout.FlowRow

/** One BoomChat "chatbox" skin: bubble fill, text color, avatar border color. */
private data class Skin(val fill: Brush, val text: Color, val border: Color)

private fun skinFor(rank: UserRank): Skin = when (rank) {
    // Owner / manager: GOLD background (الخلفية ذهبية) + dark text — reserved for staff.
    UserRank.OWNER -> Skin(Brush.horizontalGradient(listOf(Color(0xFFFFEDA5), Color(0xFFCC9835))), Color(0xFF3A2A00), Color(0xFF856211))
    // Admin / moderator: black bubble + silver (الاسود الفضي) — reserved for staff.
    UserRank.MODERATOR -> Skin(Brush.horizontalGradient(listOf(Color(0xFF1E1E1E), Color(0xFF000000))), Color(0xFFE6E6E6), Color(0xFFCFCFCF))
    UserRank.VIP_DIAMOND -> Skin(Brush.horizontalGradient(listOf(Color(0xFFFFE1E8), Color(0xFFFD62BE))), Color(0xFF3A0A28), Color(0xFFFD62BE))
    // Regular / guest: bright silver-grey background, dark text (not dark grey).
    UserRank.REGULAR -> Skin(Brush.horizontalGradient(listOf(Color(0xFFECECEC), Color(0xFFD8D8D8))), Color(0xFF333333), Color(0xFFBDBDBD))
    UserRank.BOT -> Skin(Brush.horizontalGradient(listOf(Color(0xFF233548), Color(0xFF233548))), Color.White, Color(0xFF00E5FF))
}

/**
 * Message row styled like iqchat.top (BoomChat Yellow theme). The screen runs right-to-left,
 * so the avatar sits on the right and the time with its clock sits on the left, as on the site.
 */
@Composable
fun MessageBubble(
    message: ChatMessage,
    onUserMention: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val loader = remember { svgCapableLoader(context) }
    val rankSkin = skinFor(message.senderRank)
    // Female members default to a pink chatbox skin (iqchat convention) when they
    // are regular members and have not picked their own color.
    val isFemale = message.senderGender == "female" || message.senderGender == "أنثى" || message.senderGender == "انثى"
    val baseSkin = if (message.customHexColor == null && message.senderRank == UserRank.REGULAR && isFemale)
        rankSkin.copy(fill = Brush.horizontalGradient(listOf(Color(0xFFFAD5F6), Color(0xFFFAD5F6))), text = Color(0xFF7A2960), border = Color(0xFFE873C8))
    else rankSkin
    val isStaffRank = message.senderRank == UserRank.OWNER || message.senderRank == UserRank.MODERATOR
    // Everyone (staff included) may pick any color; without a pick, the rank
    // default applies (gold owner / black mod / grey member / pink female).
    val skin = message.customHexColor?.let { hex ->
        runCatching {
            val c = Color(android.graphics.Color.parseColor(hex))
            val lum = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
            baseSkin.copy(fill = Brush.horizontalGradient(listOf(c, c)), text = if (lum > 0.6f) Color(0xFF222222) else Color.White)
        }.getOrNull()
    } ?: baseSkin
    val avatarShape = RoundedCornerShape(14.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Large rounded-square avatar on the right, like iqchat.top (~60dp).
        // Staff get a thick, solid frame (gold=owner, silver=mod) so it's unmistakable.
        val frameWidth = if (isStaffRank) 4.dp else 2.dp
        Box(
            modifier = Modifier
                .size(60.dp)
                .border(frameWidth, skin.border.copy(alpha = if (isStaffRank) 1f else 0.9f), avatarShape)
                .padding(2.dp)
                .clip(avatarShape)
                .background(Color(colorForName(message.senderName)))
                .clickable { onUserMention(message.senderName) },
            contentAlignment = Alignment.Center
        ) {
            Text(message.senderName.trim().take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            if (message.senderAvatar.isNotBlank()) {
                AsyncImage(
                    model = message.senderAvatar,
                    imageLoader = loader,
                    contentDescription = message.senderName,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Rank is NOT shown on the avatar (client's request); it appears as a
            // small icon next to the time/name inside the bubble instead.
        }

        Spacer(Modifier.width(8.dp))

        // Bubble hugs its content so the frame fits the username length (short = small,
        // long / decorated names = wider), like iqchat.top.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .widthIn(min = 110.dp)
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onUserMention(message.senderName) }
                .background(skin.fill)
                .drawBehind {
                    // iqchat .chatbox has a thick colored left border + soft shadow
                    drawRect(skin.border, topLeft = androidx.compose.ui.geometry.Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(6f, size.height))
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.senderName,
                    color = skin.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { onUserMention(message.senderName) }
                )
                Spacer(Modifier.width(4.dp))
                Text(message.senderRank.badge, fontSize = 11.sp)   // .chat_rank icon
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = if (message.senderRank == UserRank.BOT) Color(0xFFB0BEC5) else skin.text.copy(alpha = 0.7f),
                    modifier = Modifier.size(11.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    message.timestamp,
                    color = if (message.senderRank == UserRank.BOT) Color(0xFFB0BEC5) else skin.text.copy(alpha = 0.8f),
                    fontSize = 10.5.sp
                )
                if (message.senderRank != UserRank.BOT) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Block,
                        contentDescription = "حظر",
                        tint = skin.text.copy(alpha = 0.6f),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            if (message.senderRank == UserRank.BOT) {
                // Quiz bot lines are shown as colored pills (cyan / purple) like the site's quizbot.
                val pill = if (message.text.contains("تلميح")) Color(0xFFA100E8) else Color(0xFF00B4D8)
                Text(
                    text = message.text,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(pill)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            } else {
                // Media handling (Image or Audio voice notes)
                if (message.mediaType == "image" && !message.mediaUrl.isNullOrBlank()) {
                    val fullUrl = if (message.mediaUrl.startsWith("http")) message.mediaUrl else "${AppConfig.SERVER_URL}${message.mediaUrl}"
                    val context = LocalContext.current
                    val loader = remember { gifCapableLoader(context) }
                    AsyncImage(
                        model = fullUrl,
                        imageLoader = loader,
                        contentDescription = "صورة",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    if (message.text.isNotBlank() && message.text != "📷 صورة" && message.text != "📷 صورة خاصة") {
                        Spacer(Modifier.height(4.dp))
                        EmoticonText(text = message.text, color = skin.text)
                    }
                } else if (message.mediaType == "audio" && !message.mediaUrl.isNullOrBlank()) {
                    val fullUrl = if (message.mediaUrl.startsWith("http")) message.mediaUrl else "${AppConfig.SERVER_URL}${message.mediaUrl}"
                    AudioBubblePlayer(
                        audioUrl = fullUrl,
                        durationSec = message.audioDuration ?: 0,
                        textColor = skin.text
                    )
                } else {
                    EmoticonText(text = message.text, color = skin.text)
                }
            }
        }
    }
}

@Composable
private fun AudioBubblePlayer(audioUrl: String, durationSec: Int, textColor: Color) {
    var isPlaying by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    DisposableEffect(audioUrl) {
        onDispose {
            player?.release()
            player = null
        }
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x18000000))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                if (isPlaying) {
                    player?.pause()
                    isPlaying = false
                } else {
                    if (player == null) {
                        try {
                            val mp = android.media.MediaPlayer().apply {
                                setDataSource(audioUrl)
                                prepareAsync()
                                setOnPreparedListener {
                                    start()
                                    isPlaying = true
                                }
                                setOnCompletionListener {
                                    isPlaying = false
                                }
                                setOnErrorListener { _, _, _ ->
                                    isPlaying = false
                                    true
                                }
                            }
                            player = mp
                        } catch (e: Exception) {
                            isPlaying = false
                        }
                    } else {
                        player?.start()
                        isPlaying = true
                    }
                }
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "إيقاف" else "تشغيل",
                tint = textColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                text = if (isPlaying) "▶ جاري الاستماع..." else "🎤 تسجيل صوتي",
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            val durText = if (durationSec > 0) "${durationSec} ثانية" else "صوت"
            Text(text = durText, color = textColor.copy(alpha = 0.7f), fontSize = 10.sp)
        }
    }
}


@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EmoticonText(text: String, color: Color) {
    val context = LocalContext.current
    val known = remember { Emoticons.codes(context).toSet() }
    if (known.isEmpty() || !text.contains(':')) {
        Text(text, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        return
    }
    val loader = remember { gifCapableLoader(context) }
    val tokens = remember(text) { Emoticons.tokenize(text, known) }
    FlowRow(verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        tokens.forEach { tk ->
            when (tk) {
                is Emoticons.Token.Text -> Text(tk.value, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                is Emoticons.Token.Emoticon -> AsyncImage(
                    model = Emoticons.assetUri(tk.code),
                    imageLoader = loader,
                    contentDescription = tk.code,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
