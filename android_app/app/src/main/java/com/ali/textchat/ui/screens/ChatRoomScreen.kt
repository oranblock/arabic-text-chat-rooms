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
import com.ali.textchat.model.PmThread
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
    val ytTitle by socket.youtubeTitle.collectAsState()
    val notifications by socket.notifications.collectAsState()
    val threads by socket.threads.collectAsState()
    val requests by socket.requests.collectAsState()

    var input by remember { mutableStateOf("") }
    var showEmoji by remember { mutableStateOf(false) }
    var showRooms by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var modTarget by remember { mutableStateOf<ChatUser?>(null) }
    var privTarget by remember { mutableStateOf<ChatUser?>(null) }
    var ytVisible by remember { mutableStateOf(true) }
    var showNotifs by remember { mutableStateOf(false) }
    var showInbox by remember { mutableStateOf(false) }
    var showRequests by remember { mutableStateOf(false) }
    var showAdminPanel by remember { mutableStateOf(false) }

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
                if (isStaff(me?.rank)) {
                    HeadOption(Icons.Default.Security, 0) { showAdminPanel = true }
                }
                HeadOption(Icons.Default.Notifications, notifications.size) { showNotifs = true }
                HeadOption(Icons.Default.Email, 0) { socket.loadThreads(); showInbox = true }
                HeadOption(Icons.Default.PersonAdd, requests.size) { socket.loadRequests(); showRequests = true }
                HeadOption(Icons.Default.AccountCircle, 0) { showProfile = true }
                HeadOption(Icons.Default.People, users.size) { scope.launch { drawerState.open() } }
            }

            room?.topic?.takeIf { it.isNotBlank() }?.let { t ->
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFFDCE6FF)).padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Campaign, null, tint = Color(0xFF0A1E4D), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t, color = Color(0xFF0A1E4D), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                    if (!ytId.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (ytVisible) Color(0xFFE50914) else Color(0xFF1E293B))
                                .clickable { ytVisible = !ytVisible }
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (ytVisible) "📺 إخفاء" else "▶ يوتيوب", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().background(BcChatBackground)) {
                if (ytVisible && !ytId.isNullOrBlank()) {
                    YouTubeInChatPlayer(
                        videoId = ytId ?: "",
                        videoTitle = ytTitle ?: "يوتيوب مشترك في الغرفة",
                        onClose = { ytVisible = false }
                    )
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
        ProfileDialog(u, onLogout = { showProfile = false; onLogout() }, onDismiss = { showProfile = false }) { color, avatar, status ->
            socket.updateProfile(color, avatar, status) { _, _ -> }; showProfile = false
        }
    }
    if (showNotifs) NotificationsDialog(notifications, onClear = { socket.clearNotifications(); showNotifs = false }, onDismiss = { showNotifs = false })
    if (showInbox) InboxDialog(threads, onPick = { t ->
        showInbox = false
        privTarget = ChatUser(t.userId, t.name, t.avatarUrl, t.rank, null, "", false, false)
        socket.loadPrivateHistory(t.userId)
    }, onDismiss = { showInbox = false })
    if (showRequests) RequestsDialog(requests,
        onRespond = { id, ok -> socket.respondRequest(id, ok) }, onDismiss = { showRequests = false })
    if (showAdminPanel && isStaff(me?.rank)) {
        AdminPanelDialog(
            socket = socket,
            me = me,
            currentRoom = room,
            onDismiss = { showAdminPanel = false }
        )
    }
    modTarget?.let { t ->
        ModerationDialog(
            target = t,
            isOwner = me?.rank == UserRank.OWNER,
            onDismiss = { modTarget = null },
            onAction = { action -> socket.moderate(action, t.id); modTarget = null },
            onPromote = { rankStr -> socket.promote(t.id, rankStr); modTarget = null }
        )
    }
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
private fun ProfileDialog(me: ChatUser, onLogout: () -> Unit, onDismiss: () -> Unit, onSave: (color: String?, avatar: String?, status: String?) -> Unit) {
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
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFFD32F2F), RoundedCornerShape(6.dp))
                .clickable { onLogout() }, contentAlignment = Alignment.Center) {
                Text("تسجيل الخروج", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NotificationsDialog(items: List<String>, onClear: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).padding(14.dp).fillMaxWidth(0.9f).heightIn(max = 460.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("الإشعارات", color = BcAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (items.isNotEmpty()) Text("مسح", color = Color(0xFF888888), fontSize = 13.sp, modifier = Modifier.clickable { onClear() })
            }
            Spacer(Modifier.height(10.dp))
            if (items.isEmpty()) Text("لا توجد إشعارات", color = Color(0xFF999999), fontSize = 13.sp)
            LazyColumn {
                items(items.reversed()) { line ->
                    Text(line, color = Color(0xFF333333), fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    HorizontalDivider(color = BcInputBorder)
                }
            }
        }
    }
}

@Composable
private fun InboxDialog(threads: List<PmThread>, onPick: (PmThread) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).padding(14.dp).fillMaxWidth(0.9f).heightIn(max = 460.dp)) {
            Text("الرسائل الخاصة", color = BcAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            if (threads.isEmpty()) Text("لا توجد محادثات خاصة", color = Color(0xFF999999), fontSize = 13.sp)
            LazyColumn {
                items(threads) { t ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(t) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${t.rank.badge} ", fontSize = 14.sp)
                        Column(Modifier.weight(1f)) {
                            Text(t.name, color = Color(0xFF333333), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(t.lastText, color = Color(0xFF888888), fontSize = 12.sp, maxLines = 1)
                        }
                        Icon(Icons.Default.Email, null, tint = BcAccent, modifier = Modifier.size(18.dp))
                    }
                    HorizontalDivider(color = BcInputBorder)
                }
            }
        }
    }
}

@Composable
private fun RequestsDialog(requests: List<PmThread>, onRespond: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).padding(14.dp).fillMaxWidth(0.9f).heightIn(max = 460.dp)) {
            Text("طلبات الإضافة", color = BcAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            if (requests.isEmpty()) Text("لا توجد طلبات", color = Color(0xFF999999), fontSize = 13.sp)
            LazyColumn {
                items(requests) { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${r.rank.badge} ", fontSize = 14.sp)
                        Text(r.name, color = Color(0xFF333333), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(BcHeaderEnd).clickable { onRespond(r.userId, true) }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text("قبول", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, Color(0xFFBBBBBB), RoundedCornerShape(6.dp)).clickable { onRespond(r.userId, false) }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text("تجاهل", color = Color(0xFF888888), fontSize = 12.sp)
                        }
                    }
                    HorizontalDivider(color = BcInputBorder)
                }
            }
        }
    }
}

@Composable
private fun ModerationDialog(
    target: ChatUser,
    isOwner: Boolean,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit,
    onPromote: (String) -> Unit
) {
    val actions = listOf(
        "unmute" to "🔊 فك الكتم / تفعيل العضو",
        "mute" to "🔇 كتم العضو",
        "ghost" to "👻 وضع الشبح (كتم صامت)",
        "unghost" to "👁️ إلغاء وضع الشبح",
        "kick" to "🚪 طرد من الغرفة",
        "ban_device" to "⛔ حظر الجهاز نهائياً"
    )
    Dialog(onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .padding(16.dp)
                .fillMaxWidth(0.9f)
                .heightIn(max = 520.dp)
        ) {
            Text("إدارة: ${target.name} (${target.rank.titleAr})", color = BcAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            Text("الرتبة والترقيات:", fontSize = 12.sp, color = Color(0xFF666666), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isOwner) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(Color(0xFFEDE7F6)).clickable { onPromote("MODERATOR") }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("🛡️ مشرف", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF512DA8)) }
                }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(Color(0xFFE1F5FE)).clickable { onPromote("VIP_DIAMOND") }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) { Text("💎 مميز", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0288D1)) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(Color(0xFFF5F5F5)).clickable { onPromote("REGULAR") }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) { Text("👤 عادي", fontSize = 12.sp, color = Color(0xFF616161)) }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = BcInputBorder)

            LazyColumn(Modifier.fillMaxWidth()) {
                items(actions) { (a, label) ->
                    Text(
                        label,
                        color = if (a == "ban_device") Color(0xFFD32F2F) else if (a == "unmute") Color(0xFF2E7D32) else Color(0xFF333333),
                        fontSize = 14.sp,
                        fontWeight = if (a == "unmute" || a == "ban_device") FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth().clickable { onAction(a) }.padding(vertical = 10.dp)
                    )
                    HorizontalDivider(color = BcInputBorder)
                }
            }
        }
    }
}

@Composable
private fun AdminPanelDialog(
    socket: ChatSocket,
    me: ChatUser?,
    currentRoom: ChatRoom?,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var broadcastText by remember { mutableStateOf("") }
    var staffList by remember { mutableStateOf<List<ChatUser>>(emptyList()) }
    var bannedList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var lockPub by remember { mutableStateOf(currentRoom?.isLocked == true) }
    var lockPriv by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) socket.listStaff { staffList = it }
        if (selectedTab == 3) socket.listBanned { bannedList = it }
    }

    Dialog(onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .fillMaxWidth(0.95f)
                .heightIn(max = 540.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(BcHeaderStart, BcHeaderEnd)))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Security, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("لوحة إدارة الروم والمشرفين", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(20.dp).clickable { onDismiss() })
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF0F4FF))
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                listOf("🔒 الروم", "🛡️ المشرفين", "🤖 البوت", "⛔ الحظر").forEachIndexed { idx, title ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == idx) BcAccent else Color.Transparent)
                            .clickable { selectedTab = idx }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            title,
                            color = if (selectedTab == idx) Color.White else Color(0xFF333333),
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Column(Modifier.weight(1f).fillMaxWidth().padding(14.dp)) {
                when (selectedTab) {
                    0 -> {
                        Text("التحكم بالغرفة: ${currentRoom?.title ?: "ديوانية العراق"}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BcAccent)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("قفل الشات العام", fontSize = 13.sp, color = Color(0xFF333333), modifier = Modifier.weight(1f))
                            Switch(checked = lockPub, onCheckedChange = {
                                lockPub = it
                                socket.lockRoom("public", it)
                            })
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("قفل المحادثات الخاصة", fontSize = 13.sp, color = Color(0xFF333333), modifier = Modifier.weight(1f))
                            Switch(checked = lockPriv, onCheckedChange = {
                                lockPriv = it
                                socket.lockRoom("private", it)
                            })
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("📢 إرسال تعميم / برودكاست لجميع الغرف", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF333333))
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier.fillMaxWidth().height(42.dp).background(BcInputFill, RoundedCornerShape(6.dp))
                                .border(1.dp, BcInputBorder, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (broadcastText.isEmpty()) Text("اكتب نص الإشعار العام هنا...", color = Color(0xFF9E9E9E), fontSize = 12.sp)
                            BasicTextField(broadcastText, { broadcastText = it }, singleLine = true, textStyle = TextStyle(color = Color(0xFF181818), fontSize = 13.sp), modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(6.dp)).background(BcAccent)
                                .clickable {
                                    if (broadcastText.isNotBlank()) {
                                        socket.broadcast(broadcastText.trim())
                                        broadcastText = ""
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("إرسال البرودكاست 📢", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    1 -> {
                        Text("طاقم الإدارة والمشرفين", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BcAccent)
                        Spacer(Modifier.height(4.dp))
                        Text("المدير المؤسس: علي (demo123) 👑", fontSize = 11.sp, color = Color(0xFF666666))
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(staffList) { s ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.rank.badge, fontSize = 16.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(s.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF222222))
                                        Text(s.rank.titleAr, fontSize = 11.sp, color = if (s.rank == UserRank.OWNER) Color(0xFFD4AF37) else Color(0xFF757575))
                                    }
                                    if (me?.rank == UserRank.OWNER && s.id != me.id) {
                                        Text(
                                            "تنزيل 👤",
                                            color = Color(0xFFD32F2F),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFFFEBEE)).clickable {
                                                socket.promote(s.id, "REGULAR")
                                                socket.listStaff { staffList = it }
                                            }.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                HorizontalDivider(color = BcInputBorder)
                            }
                        }
                    }
                    2 -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text("🤖 مسابقات الكلمات المبعثرة", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = BcAccent)
                            Spacer(Modifier.height(6.dp))
                            Text("مسابقات الكلمات المبعثرة 🎮", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00838F))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "بوت ألعاب وألغاز عراقية تفاعلي يطرح أسئلة ثقافية في الشات تلقائياً، ويكافئ أسرع إجابة صحيحة بـ 10 نقاط فوراً!",
                                fontSize = 12.sp,
                                color = Color(0xFF555555),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Box(
                                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF00838F))
                                    .clickable {
                                        socket.triggerQuiz()
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🎯 طرح سؤال مسابقة الآن بالشات", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                    3 -> {
                        Text("الأجهزة المحظورة (عتاد الجهاز)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BcAccent)
                        Spacer(Modifier.height(8.dp))
                        if (bannedList.isEmpty()) {
                            Text("لا توجد أجهزة محظورة حالياً 👍", fontSize = 12.sp, color = Color(0xFF777777))
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(bannedList) { (hash, by) ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(hash.take(16) + "...", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF333333))
                                            Text("بواسطة: $by", fontSize = 10.sp, color = Color(0xFF888888))
                                        }
                                        Box(
                                            Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFE8F5E9)).clickable {
                                                socket.unbanDevice(hash)
                                                socket.listBanned { bannedList = it }
                                            }.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("فك الحظر", color = Color(0xFF2E7D32), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    HorizontalDivider(color = BcInputBorder)
                                }
                            }
                        }
                    }
                }
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
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.PersonAdd, "إضافة صديق", tint = Color.White,
                    modifier = Modifier.size(20.dp).clickable { socket.sendRequest(target.id) })
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
