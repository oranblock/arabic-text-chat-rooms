package com.ali.textchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    UserRank.OWNER -> Skin(Brush.horizontalGradient(listOf(BcGoldC, BcGoldB, BcGoldA)), Color(0xFF222222), BcGoldA)
    UserRank.MODERATOR -> Skin(Brush.horizontalGradient(listOf(BcVioletB, BcVioletA)), Color(0xFFFFE7E7), BcVioletA)
    UserRank.VIP_DIAMOND -> Skin(Brush.horizontalGradient(listOf(BcSkinPurple, BcSkinPurple)), Color.White, BcSkinPurple)
    UserRank.REGULAR -> Skin(Brush.horizontalGradient(listOf(Color(0xFF4A4A4A), Color(0xFF4A4A4A))), Color.White, Color(0xFF4A4A4A))
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
    val skin = skinFor(message.senderRank)
    val avatarShape = RoundedCornerShape(10.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Compact rounded-square avatar (42dp)
        Box(
            modifier = Modifier
                .size(42.dp)
                .border(1.5.dp, skin.border.copy(alpha = 0.85f), avatarShape)
                .padding(1.dp)
                .clip(avatarShape)
                .background(Color(colorForName(message.senderName)))
                .clickable { onUserMention(message.senderName) },
            contentAlignment = Alignment.Center
        ) {
            Text(message.senderName.trim().take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (message.senderAvatar.isNotBlank()) {
                AsyncImage(
                    model = message.senderAvatar,
                    imageLoader = loader,
                    contentDescription = message.senderName,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Dynamic compact message card hugging content
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onUserMention(message.senderName) }
                .background(skin.fill)
                .padding(horizontal = 9.dp, vertical = 5.dp)
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
                // Ghost/shadowban is silent: the target sees their own message as normal,
                // no marker, so they never realise they are muted for everyone else.
                EmoticonText(text = message.text, color = skin.text)
            }
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
