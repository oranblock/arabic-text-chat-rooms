package com.ali.textchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.ali.textchat.model.ChatUser
import com.ali.textchat.model.UserRank
import com.ali.textchat.ui.theme.*

/**
 * قائمة المتصلين الجانبية (Online Users Drawer)
 * تعرض المتصلين مقسمين بحسب رتبهم مع إمكانية النقر السريع للرد
 */
@Composable
fun OnlineUsersDrawer(
    users: List<ChatUser>,
    onUserClick: (ChatUser) -> Unit,
    onClose: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "المتصلون الآن (${users.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoyalBluePrimary
                )
                TextButton(onClick = onClose) {
                    Text("إغلاق")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(users) { user ->
                    UserItemRow(user = user, onClick = { onUserClick(user) })
                }
            }
        }
    }
}

@Composable
fun UserItemRow(
    user: ChatUser,
    onClick: () -> Unit
) {
    val nameColor = when (user.rank) {
        UserRank.OWNER -> OwnerGold
        UserRank.MODERATOR -> ModeratorSilver
        UserRank.VIP_DIAMOND -> DiamondCyan
        else -> RegularUserText
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.LightGray)
        ) {
            AsyncImage(
                model = user.avatarUrl,
                contentDescription = user.name,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${user.rank.badge} ${user.name}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = nameColor
            )
            Text(
                text = user.rank.titleAr,
                fontSize = 10.sp,
                color = Color.Gray
            )
        }
    }
}
