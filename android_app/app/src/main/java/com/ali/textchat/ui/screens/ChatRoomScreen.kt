package com.ali.textchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ali.textchat.data.ChatSocket
import com.ali.textchat.model.ChatRoom
import com.ali.textchat.model.ChatUser
import com.ali.textchat.model.UserRank
import com.ali.textchat.ui.components.MessageBubble
import com.ali.textchat.ui.components.OnlineUsersDrawer
import com.ali.textchat.ui.components.YouTubeInChatPlayer
import com.ali.textchat.ui.theme.*
import kotlinx.coroutines.launch

private fun isStaff(rank: UserRank?) = rank == UserRank.OWNER || rank == UserRank.MODERATOR

@Composable
fun ChatRoomScreen(socket: ChatSocket, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val me by socket.me.collectAsState()
    val room by socket.room.collectAsState()
    val messages by socket.messages.collectAsState()
    val users by socket.users.collectAsState()
    val rooms by socket.rooms.collectAsState()
    val error by socket.errors.collectAsState()
    val ytId by socket.youtubeId.collectAsState()

    var input by remember { mutableStateOf("") }
    var showEmoji by remember { mutableStateOf(false) }
    var showRooms by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var modTarget by remember { mutableStateOf<ChatUser?>(null) }
    var privTarget by remember { mutableStateOf<ChatUser?>(null) }
    var ytVisible by remember { mutableStateOf(false) }

    LaunchedEffect(ytId) { if (!ytId.isNullOrBlank()) ytVisible = true }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1) }
    LaunchedEffect(error) { if (error != null) { kotlinx.coroutines.delay(3500); socket.clearError() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            OnlineUsersDrawer(
                users = users,
                onUserClick = { u ->
                    if (u.id != me?.id) {
                        if (isStaff(me?.rank)) modTarget = u else { privTarget = u; socket.loadPrivateHistory(u.id) }
                    }
                    scope.launch { drawerState.close() }
                },
                onClose = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Column(Modifier.fillMaxSize().background(BcBody)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(BcHeaderStart, BcHeaderEnd)))
                    .statusBarsPadding()
                    .height(54.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { showRooms = true; socket.listRooms() }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Home, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text("🇮🇶 " + (room?.title ?: "ديوانية العراق"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("${users.size} متصل", color = Color(0xFFC7D8FF), fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                HeadOption(Icons.Default.Logout, 0) { onLogout() }
                HeadOption(Icons.Default.AccountCircle, 0) { showProfile = true }
                HeadOption(Icons.Default.People, users.size) { scope.launch { drawerState.open() } }
            }

            room?.topic?.takeIf { it.isNotBlank() }?.let { t ->
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFFDCE6FF)).padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Campaign, null, tint = Color(0xFF0A1E4D), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t, color = Color(0xFF0A1E4D), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().background(BcChatBackground)) {
                if (ytVisible && !ytId.isNullOrBlank()) {
                    YouTubeInChatPlayer(videoId = ytId ?: "", videoTitle = "يوتيوب مشترك في الغرفة", onClose = { ytVisible = false })
                }
                if (error != null) {
                    Text(error!!, color = Color(0xFFA94442), fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().background(Color(0xFFF2DEDE)).padding(8.dp))
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
                    items(messages) { msg -> MessageBubble(message = msg, onUserMention = { name -> input = "@$name: $input" }) }
                }
            }

            Box(Modifier.fillMaxWidth().height(2.dp).background(BcAccent))
            if (showEmoji) EmojiAndEmoticonPicker(onEmoji = { input += it }, onEmoticon = { input += " :$it: " })

            Row(
                modifier = Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().imePadding()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showEmoji = !showEmoji }, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.SentimentSatisfied, "إيموجي", tint = BcAccent)
                }
                Box(
                    modifier = Modifier.weight(1f).height(40.dp).background(BcInputFill, RoundedCornerShape(3.dp))
                        .border(1.dp, BcInputBorder, RoundedCornerShape(3.dp)).padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (input.isEmpty()) Text("اكتب رسالتك...", color = Color(0xFF9E9E9E), fontSize = 14.sp)
                    BasicTextField(input, { input = it }, singleLine = true,
                        textStyle = TextStyle(color = Color(0xFF181818), fontSize = 14.sp), modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier.size(48.dp, 40.dp).background(BcAccent, RoundedCornerShape(3.dp)).clickable {
                        if (input.isNotBlank()) { socket.sendMessage(input.trim()); input = "" }
                    },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Filled.Send, "إرسال", tint = Color.White, modifier = Modifier.size(20.dp)) }
            }
        }
    }

    if (showRooms) RoomsDialog(rooms, room?.id, onPick = { socket.joinRoom(it); showRooms = false }, onDismiss = { showRooms = false })
    if (showProfile) me?.let { u ->
        ProfileDialog(u, onDismiss = { showProfile = false }) { color, avatar, status ->
            socket.updateProfile(color, avatar, status) { _, _ -> }; showProfile = false
        }
    }
    modTarget?.let { t -> ModerationDialog(t, onDismiss = { modTarget = null }) { action -> socket.moderate(action, t.id); modTarget = null } }
    privTarget?.let { t -> PrivateChatDialog(socket, t, onDismiss = { privTarget = null }) }
}

@Composable
private fun HeadOption(icon: ImageVector, count: Int, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        if (count > 0) Box(
            Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 2.dp).size(16.dp).background(BcNotify, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(if (count > 99) "99" else "$count", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun EmojiAndEmoticonPicker(onEmoji: (String) -> Unit, onEmoticon: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val codes = remember { com.ali.textchat.ui.util.Emoticons.codes(context) }
    val loader = remember { com.ali.textchat.ui.util.gifCapableLoader(context) }
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        if (codes.isNotEmpty()) {
            androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxWidth().padding(6.dp)) {
                items(codes.size) { i ->
                    coil.compose.AsyncImage(
                        model = com.ali.textchat.ui.util.Emoticons.assetUri(codes[i]),
                        imageLoader = loader, contentDescription = codes[i],
                        modifier = Modifier.padding(4.dp).size(34.dp).clickable { onEmoticon(codes[i]) }
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(BcInputBorder))
        }
        val emojis = listOf("😀","😂","😍","😎","😭","😅","🤣","😊","😘","🥰","😔","😢","👍","👏","🙏","💪","🔥","❤️","💔","💯","🌹","🎉","✨","⭐","😡","🤔","😴","🤯","🥳","😇","🙈","💎","👑","🇮🇶","🎵","☕")
        LazyVerticalGrid(GridCells.Fixed(8), modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp).padding(6.dp)) {
            items(emojis.size) { i -> Text(emojis[i], fontSize = 22.sp, modifier = Modifier.padding(4.dp).clickable { onEmoji(emojis[i]) }) }
        }
    }
}

@Composable
private fun RoomsDialog(rooms: List<ChatRoom>, currentId: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).padding(14.dp).fillMaxWidth(0.9f)) {
            Text("قائمة الرومات", color = BcAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            rooms.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (r.id == currentId) BcUserItemEnd else BcInputFill)
                        .clickable { onPick(r.id) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Home, null, tint = BcAccent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.title, color = Color(0xFF333333), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(r.description, color = Color(0xFF888888), fontSize = 11.sp)
                    }
                    Text("${r.onlineCount}", color = Color.White, fontSize = 11.sp,
                        modifier = Modifier.background(BcHeaderEnd, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileDialog(me: ChatUser, onDismiss: () -> Unit, onSave: (color: String?, avatar: String?, status: String?) -> Unit) {
    val colors = listOf("#D31027", "#7929FF", "#03ADD8", "#28C76F", "#CC9835", "#CE34E9", "#2196F3", "#FF9800")
    val avatarSeeds = listOf("Iraq", "Baghdad", "Basra", "Najaf", "Karbala", "Mosul", "Kufa", "Anbar")
    var picked by remember { mutableStateOf(me.customHexColor) }
    var avatar by remember { mutableStateOf(me.avatarUrl.ifBlank { null }) }
    val vip = me.rank == UserRank.VIP_DIAMOND || me.rank == UserRank.MODERATOR || me.rank == UserRank.OWNER
    val loader = com.ali.textchat.ui.util.svgCapableLoader(androidx.compose.ui.platform.LocalContext.current)
    Dialog(onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).padding(16.dp).fillMaxWidth(0.9f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(me.name, color = BcAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("${me.rank.badge} ${me.rank.titleAr}", color = Color(0xFF666666), fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            Text("اختر صورتك", color = Color(0xFF444444), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyRow {
                items(avatarSeeds.size) { i ->
                    val url = "https://api.dicebear.com/7.x/bottts/png?seed=${avatarSeeds[i]}"
                    Box(
                        Modifier.padding(4.dp).size(48.dp).clip(RoundedCornerShape(12.dp))
                            .border(if (avatar == url) 3.dp else 1.dp, if (avatar == url) BcAccent else BcInputBorder, RoundedCornerShape(12.dp))
                            .clickable { avatar = url }
                    ) {
                        coil.compose.AsyncImage(model = url, imageLoader = loader, contentDescription = null, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(if (vip) "اختر لون اسمك" else "تغيير اللون للأعضاء المميزين", color = Color(0xFF444444), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row {
                colors.forEach { c ->
                    Box(
                        Modifier.padding(4.dp).size(30.dp).clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(c)))
                            .border(if (picked == c) 3.dp else 0.dp, Color.Black, CircleShape)
                            .clickable(enabled = vip) { picked = c }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(6.dp)).background(BcAccent)
                .clickable { onSave(if (vip) picked else null, avatar, "online") }, contentAlignment = Alignment.Center) {
                Text("حفظ", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ModerationDialog(target: ChatUser, onDismiss: () -> Unit, onAction: (String) -> Unit) {
    val actions = listOf(
        "mute" to "🔇 كتم", "unmute" to "🔊 فك الكتم / تفعيل", "ghost" to "👻 وضع الشبح",
        "unghost" to "👁️ إلغاء الشبح", "kick" to "🚪 طرد", "ban_device" to "⛔ حظر الجهاز نهائياً"
    )
    Dialog(onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).padding(14.dp).fillMaxWidth(0.85f)) {
            Text("إدارة: ${target.name}", color = BcAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            actions.forEach { (a, label) ->
                Text(label, color = Color(0xFF333333), fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().clickable { onAction(a) }.padding(vertical = 10.dp))
                HorizontalDivider(color = BcInputBorder)
            }
        }
    }
}

@Composable
private fun PrivateChatDialog(socket: ChatSocket, target: ChatUser, onDismiss: () -> Unit) {
    val thread by socket.privates.collectAsState()
    var text by remember { mutableStateOf("") }
    Dialog(onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).fillMaxWidth(0.92f).heightIn(max = 480.dp)) {
            Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(BcHeaderStart, BcHeaderEnd))).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Email, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("خاص مع ${target.name}", color = Color.White, fontWeight = FontWeight.Bold)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().background(BcChatBackground).padding(8.dp)) {
                items(thread) { m -> Text("${m.senderName}: ${m.text}", color = Color(0xFF333333), fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp)) }
            }
            Row(Modifier.fillMaxWidth().background(Color.White).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(40.dp).background(BcInputFill, RoundedCornerShape(3.dp)).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text("رسالة خاصة...", color = Color(0xFF9E9E9E), fontSize = 13.sp)
                    BasicTextField(text, { text = it }, singleLine = true, textStyle = TextStyle(color = Color(0xFF181818), fontSize = 14.sp), modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(46.dp, 40.dp).background(BcAccent, RoundedCornerShape(3.dp)).clickable {
                    if (text.isNotBlank()) { socket.sendPrivate(target.id, text.trim()); text = "" }
                }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.Send, "إرسال", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
