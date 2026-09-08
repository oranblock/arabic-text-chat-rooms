package com.ali.textchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ali.textchat.model.ChatMessage
import com.ali.textchat.model.UserRank
import com.ali.textchat.ui.theme.*

/**
 * فقاعة الرسالة المصممة خصيصاً وفق متطلبات العميل:
 * 1. أفاتار مربع بزوايا منحنية (42dp × 42dp مع 10dp corner radius)
 * 2. التوقيت أقصى اليسار بخط صغير رمادي
 * 3. رتب وألوان مخصصة
 * 4. النقر السريع على الاسم أو الصورة لنسخ الاسم في حقل الكتابة
 */
@Composable
fun MessageBubble(
    message: ChatMessage,
    onUserMention: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val nameColor = when {
        message.customHexColor != null -> try {
            Color(android.graphics.Color.parseColor(message.customHexColor))
        } catch (e: Exception) {
            RegularUserText
        }
        message.senderRank == UserRank.OWNER -> OwnerGold
        message.senderRank == UserRank.MODERATOR -> ModeratorSilver
        message.senderRank == UserRank.VIP_DIAMOND -> DiamondCyan
        else -> RegularUserText
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // Rounded Square Avatar (42dp x 42dp, 10dp radius)
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.LightGray)
                .clickable { onUserMention(message.senderName) }
        ) {
            AsyncImage(
                model = message.senderAvatar,
                contentDescription = message.senderName,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Message Card Body
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (message.isGhost) GhostBubbleBackground else MessageBubbleBackground,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (message.isGhost) GhostBorder else BubbleBorder
            ),
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Header: Sender Name, Badge, and Left-Aligned Timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onUserMention(message.senderName) }
                    ) {
                        Text(
                            text = "${message.senderRank.badge} ${message.senderName}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = nameColor
                        )
                        if (message.isGhost) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "(وضع الشبح)",
                                fontSize = 10.sp,
                                color = Color(0xFF9333EA),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Left-Aligned Timestamp (HH:mm)
                    Text(
                        text = message.timestamp,
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Message Text Body
                Text(
                    text = message.text,
                    fontSize = 14.sp,
                    color = RegularUserText,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
