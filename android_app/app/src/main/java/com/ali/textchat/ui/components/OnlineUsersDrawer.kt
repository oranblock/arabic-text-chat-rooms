package com.ali.textchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ali.textchat.model.ChatUser
import com.ali.textchat.model.UserRank
import com.ali.textchat.ui.theme.*
import com.ali.textchat.ui.util.colorForName
import com.ali.textchat.ui.util.svgCapableLoader
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext

/** Right panel of iqchat: panel bar (friends / rooms / close) and grouped member list. */
@Composable
fun OnlineUsersDrawer(
    users: List<ChatUser>,
    onUserClick: (ChatUser) -> Unit,
    onClose: () -> Unit
) {
    val interactive = users.filter { it.rank == UserRank.REGULAR && !it.isMuted }
    val premium = users.filter { it.rank == UserRank.VIP_DIAMOND || it.rank == UserRank.OWNER || it.rank == UserRank.MODERATOR }
    val bots = users.filter { it.rank == UserRank.BOT }
    val muted = users.filter { it.isMuted }

    ModalDrawerSheet(drawerContainerColor = Color.White, modifier = Modifier.width(300.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(BcBody).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { }) { Icon(Icons.Default.PersonAdd, "قائمة الأصدقاء", tint = BcAccent) }
            IconButton(onClick = { }) { Icon(Icons.Default.Home, "قائمة الرومات", tint = BcAccent) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "إغلاق", tint = Color(0xFF666666)) }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            item { GroupHeader("الأعضاء المتفاعلين", interactive.size) }
            interactive.forEach { u -> item { UserItem(u, onUserClick) } }
            item { GroupHeader("الأعضاء المميزين", premium.size) }
            premium.forEach { u -> item { UserItem(u, onUserClick) } }
            item { GroupHeader("الأعضاء المتصلين", users.size - muted.size) }
            bots.forEach { u -> item { UserItem(u, onUserClick) } }
            item { GroupHeader("غير متصل", muted.size) }
            muted.forEach { u -> item { UserItem(u, onUserClick, offline = true) } }
        }
    }
}

@Composable
private fun GroupHeader(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color(0xFF444444), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text(
            "$count", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.background(BcAccent, RoundedCornerShape(10.dp)).padding(horizontal = 7.dp, vertical = 1.dp)
        )
    }
}

/** .user_item: 20px radius, white→#E0EFFF gradient, 5px #CE34E9 side borders (.offline uses pink). */
@Composable
private fun UserItem(user: ChatUser, onClick: (ChatUser) -> Unit, offline: Boolean = false) {
    val end = if (offline) Color(0xFFFFE4E9) else BcUserItemEnd
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(end, Color.White)))
            .border(width = 2.dp, color = BcAccent.copy(alpha = 0.55f), shape = RoundedCornerShape(20.dp))
            .clickable { onClick(user) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(18)).background(Color(colorForName(user.name))),
            contentAlignment = Alignment.Center
        ) {
            Text(user.name.trim().take(1), color = Color.White, fontWeight = FontWeight.Bold)
            if (user.avatarUrl.isNotBlank()) {
                AsyncImage(model = user.avatarUrl, imageLoader = svgCapableLoader(LocalContext.current),
                    contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(user.name, color = Color(0xFF535353), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (offline) "غير متصل" else "متصل", color = Color(0xFF999999), fontSize = 10.sp)
        }
        Text("🇮🇶", fontSize = 13.sp)                      // .list_flag
        Spacer(Modifier.width(4.dp))
        Text(user.rank.badge, fontSize = 13.sp)            // .list_rank
        if (user.isMuted) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.VolumeOff, "ممنوع من الكتابة", tint = Color(0xFFFF0000), modifier = Modifier.size(14.dp))
        }
    }
}
