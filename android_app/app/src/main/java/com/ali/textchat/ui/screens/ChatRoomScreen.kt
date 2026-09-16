package com.ali.textchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.ali.textchat.util.AudioRecorderHelper
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.ui.draw.drawBehind
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatRoomScreen(socket: ChatSocket, onLogout: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
    val ytBy by socket.youtubeBy.collectAsState()
    val ytOffset by socket.youtubeOffset.collectAsState()
    val ytIsWelcome by socket.youtubeIsWelcome.collectAsState()
    val notifications by socket.notifications.collectAsState()
    val threads by socket.threads.collectAsState()
    val requests by socket.requests.collectAsState()
    val scores by socket.scores.collectAsState()

    var input by remember { mutableStateOf("") }
    var showEmoji by remember { mutableStateOf(false) }
    var showRooms by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var modTarget by remember { mutableStateOf<ChatUser?>(null) }
    var privTarget by remember { mutableStateOf<ChatUser?>(null) }
    var ytVisible by remember { mutableStateOf(false) }

    LaunchedEffect(room?.id) {
        ytVisible = true
    }
    var showNotifs by remember { mutableStateOf(false) }
    var showInbox by remember { mutableStateOf(false) }
    var showRequests by remember { mutableStateOf(false) }
    var showAdminPanel by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    var showStatus by remember { mutableStateOf(false) }
    var showPrivate by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showScores by remember { mutableStateOf(false) }

    val recorderHelper = remember { AudioRecorderHelper(context) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var isUploadingMedia by remember { mutableStateOf(false) }
    var uploadStatusText by remember { mutableStateOf("") }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (recorderHelper.start()) {
                isRecordingVoice = true
                recordingSeconds = 0
            } else {
                android.widget.Toast.makeText(context, "فشل بدء التسجيل", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            android.widget.Toast.makeText(context, "إذن الميكروفون مطلوب لتسجيل الصوت", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isUploadingMedia = true
            uploadStatusText = "جاري رفع الصورة..."
            scope.launch(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null && bytes.isNotEmpty()) {
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        socket.uploadMedia(base64, "image", "upload.jpg") { ok, url ->
                            isUploadingMedia = false
                            if (ok && url != null) {
                                socket.sendMessage("", mediaType = "image", mediaUrl = url)
                            } else {
                                scope.launch(Dispatchers.Main) {
                                    android.widget.Toast.makeText(context, "فشل رفع الصورة", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else {
                        isUploadingMedia = false
                    }
                } catch (e: Exception) {
                    isUploadingMedia = false
                    e.printStackTrace()
                }
            }
        }
    }

    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            recordingSeconds = 0
            while (isRecordingVoice) {
                kotlinx.coroutines.delay(1000)
                recordingSeconds++
                if (recordingSeconds >= 120) {
                    val (file, duration) = recorderHelper.stop()
                    isRecordingVoice = false
                    if (file != null && file.exists() && duration > 0) {
                        isUploadingMedia = true
                        uploadStatusText = "جاري رفع التسجيل الصوتي..."
                        scope.launch(Dispatchers.IO) {
                            val bytes = file.readBytes()
                            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                            socket.uploadMedia(base64, "audio", file.name) { ok, url ->
                                isUploadingMedia = false
                                file.delete()
                                if (ok && url != null) {
                                    socket.sendMessage("", mediaType = "audio", mediaUrl = url, audioDuration = duration)
                                }
                            }
                        }
                    }
                    break
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorderHelper.cancel()
        }
    }

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
            // Top bar — white with magenta round buttons, exactly like iqchat.top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .statusBarsPadding()
                    .height(58.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeadPill(Icons.Default.Home, "قائمة الرومات") { showRooms = true; socket.listRooms() }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(room?.title ?: "ديوانية العراق", color = BcAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${users.size} متصل", color = Color(0xFF9E9E9E), fontSize = 10.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("🇮🇶", fontSize = 22.sp)
                Spacer(Modifier.width(6.dp))
                RoundBtn(Icons.Default.People, badge = users.size) { scope.launch { drawerState.open() } }
            }

            if (me?.isGuest == true) {
                Row(Modifier.fillMaxWidth().background(Color(0xFFFFF3CD)).padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = Color(0xFF856404), modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("حساب مؤقت (زائر) — يُحذف تلقائياً بعد ساعة. سجّل حساب دائم للاحتفاظ برتبتك.", color = Color(0xFF856404), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                }
            }
            val currentTopic = room?.topic?.takeIf { it.isNotBlank() }
            if (currentTopic != null || !ytId.isNullOrBlank()) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFFF7E4FC)).padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentTopic != null) {
                        Icon(Icons.Default.Campaign, null, tint = Color(0xFF5B1080), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(currentTopic, color = Color(0xFF5B1080), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (!ytId.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (ytIsWelcome) Color(0xFF10B981) else (if (ytVisible) Color(0xFFE50914) else Color(0xFF1E293B)))
                                .clickable { ytVisible = !ytVisible }
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (!ytVisible) (if (ytIsWelcome) "🎬 ترحيب" else "▶ يوتيوب") else "📺 إخفاء",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().background(BcChatBackground)) {
                if (ytVisible && !ytId.isNullOrBlank()) {
                    YouTubeInChatPlayer(
                        videoId = ytId ?: "",
                        videoTitle = ytTitle ?: if (ytIsWelcome) "فيديو ترحيبي بالغرفة" else "يوتيوب مشترك في الغرفة",
                        startedBy = ytBy ?: "",
                        isWelcome = ytIsWelcome,
                        startSeconds = ytOffset,
                        onClose = { ytVisible = false },
                        onVideoEnded = { finishedId ->
                            socket.videoFinished(finishedId)
                            ytVisible = false
                        }
                    )
                }
                if (error != null) {
                    Text(error!!, color = Color(0xFFA94442), fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().background(Color(0xFFF2DEDE)).padding(8.dp))
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 58.dp, bottom = 6.dp, start = 4.dp, end = 8.dp)) {
                    items(messages) { msg ->
                        MessageBubble(
                            message = msg,
                            onUserMention = { name ->
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("user_mention", "@$name ")
                                clipboard?.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "تم نسخ التاك @$name للحافظة", android.widget.Toast.LENGTH_SHORT).show()
                                input = "@$name: $input"
                            }
                        )
                    }
                }
            }
            // Collapsible floating magenta head buttons (left edge) so they don't cover the
            // chat; a small toggle opens/closes them. Rooms pill stays top-right.
            Column(Modifier.align(Alignment.TopEnd).padding(6.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                RoundBtn(if (showMenu) Icons.Default.Close else Icons.Default.Menu, badge = if (showMenu) 0 else notifications.size + requests.size) { showMenu = !showMenu }
                if (showMenu) {
                    HeadOption(Icons.Default.AccountCircle, 0) { showAccount = true; showMenu = false }
                    HeadOption(Icons.Default.Email, 0) { socket.loadThreads(); showInbox = true; showMenu = false }
                    HeadOption(Icons.Default.Notifications, notifications.size) { showNotifs = true; showMenu = false }
                    HeadOption(Icons.Default.Article, 0) { socket.loadThreads(); showInbox = true; showMenu = false }
                    HeadOption(Icons.Default.PersonAdd, requests.size) { socket.loadRequests(); showRequests = true; showMenu = false }
                    HeadOption(Icons.Default.EmojiEvents, 0) { socket.loadScores(); showScores = true; showMenu = false }
                    if (isStaff(me?.rank)) HeadOption(Icons.Default.Security, 0) { showAdminPanel = true; showMenu = false }
                }
            }
            }

            if (isUploadingMedia) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFFEF3C7)).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = BcAccent)
                    Spacer(Modifier.width(8.dp))
                    Text(if (uploadStatusText.isNotBlank()) uploadStatusText else "جاري رفع الملف...", color = Color(0xFF92400E), fontSize = 12.sp)
                }
            }

            Box(Modifier.fillMaxWidth().height(2.dp).background(BcAccent))
            if (showEmoji) EmojiAndEmoticonPicker(onEmoji = { input += it }, onEmoticon = { input += " :$it: " })

            if (isRecordingVoice) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF1F2)).navigationBarsPadding().imePadding()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            recorderHelper.cancel()
                            isRecordingVoice = false
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Default.Delete, "إلغاء التسجيل", tint = Color(0xFFE11D48), modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    val mins = recordingSeconds / 60
                    val secs = recordingSeconds % 60
                    val timeStr = String.format("%02d:%02d", mins, secs)
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color.Red))
                        Spacer(Modifier.width(8.dp))
                        Text("تسجيل صوتي: $timeStr", color = Color(0xFFBE123C), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(BcAccent).clickable {
                            val (file, duration) = recorderHelper.stop()
                            isRecordingVoice = false
                            if (file != null && file.exists() && duration > 0) {
                                isUploadingMedia = true
                                uploadStatusText = "جاري إرسال التسجيل الصوتي..."
                                scope.launch(Dispatchers.IO) {
                                    val bytes = file.readBytes()
                                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                    socket.uploadMedia(base64, "audio", file.name) { ok, url ->
                                        isUploadingMedia = false
                                        file.delete()
                                        if (ok && url != null) {
                                            socket.sendMessage("", mediaType = "audio", mediaUrl = url, audioDuration = duration)
                                        } else {
                                            scope.launch(Dispatchers.Main) {
                                                android.widget.Toast.makeText(context, "فشل إرسال التسجيل الصوتي", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "إرسال الصوت", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().imePadding()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(BcAccent).clickable {
                            if (input.isNotBlank()) { socket.sendMessage(input.trim()); input = "" }
                        },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.AutoMirrored.Filled.Send, "إرسال", tint = Color.White, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(38.dp).clip(CircleShape).clickable { showEmoji = !showEmoji }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.SentimentSatisfied, "إيموجي", tint = BcAccent, modifier = Modifier.size(24.dp))
                    }
                    Box(Modifier.size(38.dp).clip(CircleShape).clickable {
                        imagePickerLauncher.launch("image/*")
                    }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PhotoCamera, "إرسال صورة", tint = BcAccent, modifier = Modifier.size(24.dp))
                    }
                    Box(Modifier.size(38.dp).clip(CircleShape).clickable {
                        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            if (recorderHelper.start()) {
                                isRecordingVoice = true
                                recordingSeconds = 0
                            } else {
                                android.widget.Toast.makeText(context, "تعذر تشغيل الميكروفون", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Mic, "تسجيل صوتي", tint = BcAccent, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier.weight(1f).height(40.dp)
                            .drawBehind {
                                drawLine(BcAccent, androidx.compose.ui.geometry.Offset(0f, size.height - 2f),
                                    androidx.compose.ui.geometry.Offset(size.width, size.height - 2f), strokeWidth = 3f)
                            }.padding(horizontal = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (input.isEmpty()) Text("اكتب رسالتك...", color = Color(0xFF9E9E9E), fontSize = 14.sp)
                        BasicTextField(input, { input = it }, singleLine = true,
                            textStyle = TextStyle(color = Color(0xFF181818), fontSize = 14.sp), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    if (showRooms) RoomsDialog(rooms, room?.id, onPick = {
        socket.joinRoom(it)
        com.ali.textchat.data.Session.saveLastRoom(context, it)
        ytVisible = true
        showRooms = false
    }, onDismiss = { showRooms = false })
    if (showProfile) me?.let { u ->
        ProfileDialog(u, onLogout = { showProfile = false; onLogout() }, onDismiss = { showProfile = false }) { color, avatar, status, bio, age, gender, country ->
            socket.updateProfile(color, avatar, status, bio, age, gender, country) { _, _ -> }; showProfile = false
        }
    }
    if (showAccount) me?.let { u ->
        AccountPanelDialog(
            me = u,
            onDismiss = { showAccount = false },
            onEditData = { showAccount = false; showProfile = true },
            onEditStatus = { showAccount = false; showStatus = true },
            onManageFriends = { showAccount = false; socket.loadRequests(); showRequests = true },
            onPrivateSettings = { showAccount = false; showPrivate = true },
            onChangePassword = { showAccount = false; showPassword = true },
            onDeleteAccount = { showAccount = false; showDeleteConfirm = true },
            onLogout = { showAccount = false; onLogout() }
        )
    }
    if (showStatus) me?.let { u ->
        StatusDialog(u, onDismiss = { showStatus = false }) { statusValue ->
            socket.updateProfile(null, null, statusValue, null, null, null, null) { _, _ -> }; showStatus = false
        }
    }
    if (showPrivate) PrivateSettingsDialog(onDismiss = { showPrivate = false }) { showPrivate = false }
    if (showPassword) PasswordDialog(onDismiss = { showPassword = false }) { showPassword = false }
    if (showDeleteConfirm) DeleteAccountDialog(onDismiss = { showDeleteConfirm = false }, onConfirm = { showDeleteConfirm = false; onLogout() })
    if (showNotifs) NotificationsDialog(notifications, onClear = { socket.clearNotifications(); showNotifs = false }, onDismiss = { showNotifs = false })
    if (showInbox) InboxDialog(threads, onPick = { t ->
        showInbox = false
        privTarget = ChatUser(t.userId, t.name, t.avatarUrl, t.rank, null, "", false, false)
        socket.loadPrivateHistory(t.userId)
    }, onDismiss = { showInbox = false })
    if (showRequests) RequestsDialog(requests,
        onRespond = { id, ok -> socket.respondRequest(id, ok) }, onDismiss = { showRequests = false })
    if (showScores) ScoreboardDialog(scores, onDismiss = { showScores = false })
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
            socket = socket,
            onDismiss = { modTarget = null },
            onAction = { action -> socket.moderate(action, t.id); modTarget = null },
            onPromote = { rankStr -> socket.promote(t.id, rankStr); modTarget = null }
        )
    }
    privTarget?.let { t -> PrivateChatDialog(socket, t, onDismiss = { privTarget = null }) }
}

// Scoreboard / leaderboard of quiz points (burger-menu trophy button)
@Composable
private fun ScoreboardDialog(scores: List<PmThread>, onDismiss: () -> Unit) {
    val loader = com.ali.textchat.ui.util.svgCapableLoader(androidx.compose.ui.platform.LocalContext.current)
    Dialog(onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).fillMaxWidth(0.94f).heightIn(max = 620.dp)) {
            Box(Modifier.fillMaxWidth().background(BcAccent).padding(12.dp)) {
                Icon(Icons.Default.Close, "إغلاق", tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).size(24.dp).clickable { onDismiss() })
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EmojiEvents, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("المتصدرون", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (scores.isEmpty()) Text("لا توجد نقاط بعد", color = Color(0xFF999999), fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            LazyColumn {
                itemsIndexed(scores) { i, s ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.lastText, color = BcAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(s.name, color = Color(0xFF333333), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(com.ali.textchat.ui.util.colorForName(s.name))), contentAlignment = Alignment.Center) {
                            if (s.avatarUrl.isNotBlank()) coil.compose.AsyncImage(s.avatarUrl, null, imageLoader = loader, modifier = Modifier.fillMaxSize())
                            else Text(s.name.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("${i + 1}", color = Color(0xFF999999), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = BcInputBorder)
                }
            }
        }
    }
}

// ---- Account panel (حساب) + settings menu, exact iqchat.top ----

@Composable
private fun AccountPanelDialog(
    me: ChatUser,
    onDismiss: () -> Unit,
    onEditData: () -> Unit,
    onEditStatus: () -> Unit,
    onManageFriends: () -> Unit,
    onPrivateSettings: () -> Unit,
    onChangePassword: () -> Unit,
    onDeleteAccount: () -> Unit,
    onLogout: () -> Unit
) {
    Dialog(onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).fillMaxWidth(0.94f).heightIn(max = 620.dp)) {
            // Magenta header with avatar + name + close
            Box(Modifier.fillMaxWidth().background(BcAccent).padding(14.dp)) {
                Icon(Icons.Default.Close, "إغلاق", tint = Color.White, modifier = Modifier.align(Alignment.TopStart).size(26.dp).clickable { onDismiss() })
                Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                    Text(me.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                        val loader = com.ali.textchat.ui.util.svgCapableLoader(androidx.compose.ui.platform.LocalContext.current)
                        if (me.avatarUrl.isNotBlank()) coil.compose.AsyncImage(me.avatarUrl, null, imageLoader = loader, modifier = Modifier.fillMaxSize())
                        else Text(me.name.take(1), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().background(Color.White), horizontalArrangement = Arrangement.End) {
                Text("حساب", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.background(BcAccent).padding(horizontal = 22.dp, vertical = 8.dp))
            }
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                AccountRow(Icons.Default.Badge, "تحرير البيانات", onEditData)
                AccountRow(Icons.Default.HelpOutline, "تعديل الحالة", onEditStatus)
                AccountRow(Icons.Default.PersonAdd, "إدارة أصدقاء", onManageFriends)
                AccountRow(Icons.Default.Block, "إدارة التجاهل", onClick = { })
                AccountRow(Icons.AutoMirrored.Filled.Chat, "إعدادات خاصة", onPrivateSettings)
                AccountRow(Icons.Default.VpnKey, "تغيير الباسوورد", onChangePassword)
                AccountRow(Icons.Default.Delete, "الغاء الاشتراك", onDeleteAccount, danger = true)
                AccountRow(Icons.AutoMirrored.Filled.Logout, "تسجيل خروج", onLogout)
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "✨ الرسوم التعبيرية المتحركة مدعومة بواسطة\nGoogle Noto Animated Emoji & Microsoft Fluent\nتحت رخص CC-BY 4.0 و MIT مفتوحة المصدر",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.5.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountRow(icon: ImageVector, label: String, onClick: () -> Unit, danger: Boolean = false) {
    val c = if (danger) Color(0xFFD32F2F) else Color(0xFF333333)
    Column {
        Row(
            Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End
        ) {
            Text(label, color = c, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(12.dp))
            Icon(icon, null, tint = c, modifier = Modifier.size(20.dp))
        }
        HorizontalDivider(color = BcInputBorder)
    }
}

// Magenta strip + X header used by the small settings dialogs
@Composable
private fun MagentaDialogHeader(onClose: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(BcAccent).padding(10.dp)) {
        Icon(Icons.Default.Close, "إغلاق", tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).size(24.dp).clickable { onClose() })
    }
}

@Composable
private fun SaveCancelRow(onCancel: () -> Unit, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFE53935)).clickable { onCancel() }.padding(horizontal = 22.dp, vertical = 10.dp)) {
            Text("إلغاء", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Row(Modifier.clip(RoundedCornerShape(6.dp)).background(BcAccent).clickable { onSave() }.padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Save, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("حفظ", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusDialog(me: ChatUser, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val options = listOf("online" to "متصل", "away" to "بعيد", "busy" to "مشغول")
    var picked by remember { mutableStateOf("online") }
    Dialog(onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).fillMaxWidth(0.92f)) {
            MagentaDialogHeader(onDismiss)
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
                Text("تعديل الحالة", color = Color(0xFF333333), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                options.forEach { (k, label) ->
                    Row(Modifier.fillMaxWidth().clickable { picked = k }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = if (picked == k) BcAccent else Color(0xFF555555), fontWeight = if (picked == k) FontWeight.Bold else FontWeight.Normal)
                        Spacer(Modifier.width(8.dp))
                        if (picked == k) Icon(Icons.Default.CheckCircle, null, tint = BcAccent, modifier = Modifier.size(18.dp))
                    }
                }
                SaveCancelRow(onCancel = onDismiss, onSave = { onSave(picked) })
            }
        }
    }
}

@Composable
private fun PrivateSettingsDialog(onDismiss: () -> Unit, onSave: () -> Unit) {
    var on by remember { mutableStateOf(true) }
    Dialog(onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).fillMaxWidth(0.92f)) {
            MagentaDialogHeader(onDismiss)
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
                Text("دردشة خاصة", color = Color(0xFF333333), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(BcInputFill).border(1.dp, BcInputBorder, RoundedCornerShape(6.dp)).clickable { on = !on }.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFF777777))
                    Text(if (on) "تشغيل" else "إيقاف", color = Color(0xFF333333))
                }
                SaveCancelRow(onCancel = onDismiss, onSave = onSave)
            }
        }
    }
}

@Composable
private fun PasswordDialog(onDismiss: () -> Unit, onSave: () -> Unit) {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    Dialog(onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).fillMaxWidth(0.92f)) {
            MagentaDialogHeader(onDismiss)
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
                Text("تغيير الباسوورد", color = Color(0xFF333333), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                SettingsField(p1, "الرمز الجديد", isPassword = true) { p1 = it }
                Spacer(Modifier.height(8.dp))
                SettingsField(p2, "تأكيد الرمز", isPassword = true) { p2 = it }
                SaveCancelRow(onCancel = onDismiss, onSave = onSave)
            }
        }
    }
}

@Composable
private fun DeleteAccountDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).fillMaxWidth(0.92f)) {
            MagentaDialogHeader(onDismiss)
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("الغاء الاشتراك", color = Color(0xFF333333), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFE53935), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text("هل انت متأكد بأنك تريد الغاء الاشتراك؟", color = Color(0xFF666666), fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFE53935)).clickable { onDismiss() }.padding(horizontal = 22.dp, vertical = 10.dp)) {
                        Text("إلغاء", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Row(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFE53935)).clickable { onConfirm() }.padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("حذف", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsField(value: String, hint: String, isPassword: Boolean = false, onValueChange: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(44.dp).background(BcInputFill, RoundedCornerShape(6.dp))
            .border(1.dp, BcInputBorder, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        if (value.isEmpty()) Text(hint, color = Color(0xFF9E9E9E), fontSize = 14.sp)
        BasicTextField(value, onValueChange, singleLine = true,
            visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            textStyle = TextStyle(color = Color(0xFF181818), fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End), modifier = Modifier.fillMaxWidth())
    }
}

// Magenta round floating button (iqchat head-option style)
@Composable
private fun HeadOption(icon: ImageVector, count: Int, onClick: () -> Unit) {
    Box(Modifier.size(52.dp).clip(CircleShape).background(BcAccent).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        if (count > 0) Box(
            Modifier.align(Alignment.TopEnd).padding(2.dp).size(18.dp).background(BcNotify, CircleShape).border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(if (count > 99) "99" else "$count", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
    }
}

// Smaller magenta round button for the top bar (share / star / menu)
@Composable
private fun RoundBtn(icon: ImageVector, badge: Int = 0, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(BcAccent).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        if (badge > 0) Box(
            Modifier.align(Alignment.TopEnd).size(16.dp).background(BcNotify, CircleShape).border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(if (badge > 99) "99" else "$badge", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
    }
}

// Magenta rounded pill with icon + label (the "قائمة الرومات" home tab)
@Composable
private fun HeadPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(BcAccent).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmojiAndEmoticonPicker(onEmoji: (String) -> Unit, onEmoticon: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val codes = remember { com.ali.textchat.ui.util.Emoticons.codes(context) }
    val loader = remember { com.ali.textchat.ui.util.gifCapableLoader(context) }
    val lottieItems = remember { com.ali.textchat.ui.util.LottieEmojis.items }
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxWidth().background(Color.White).border(1.dp, BcInputBorder)) {
        // Tab Selector Row
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFF8FAFC)).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val tabs = listOf("✨ متحرك (Lottie)", "😀 إيموجي", "🇮🇶 تعبيرات")
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) BcAccent else Color.Transparent)
                        .clickable { selectedTab = index }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        title,
                        color = if (isSelected) Color.White else Color(0xFF64748B),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(BcInputBorder))

        when (selectedTab) {
            0 -> {
                // Tab 0: Lottie Vector Animated Emojis (Google Noto Animation)
                LazyVerticalGrid(
                    GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth().height(160.dp).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(lottieItems.size) { i ->
                        val item = lottieItems[i]
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF1F5F9))
                                .clickable { onEmoticon(item.code) }
                                .padding(vertical = 6.dp)
                        ) {
                            com.ali.textchat.ui.util.LottieEmojiView(code = item.code, size = 36.dp)
                            Spacer(Modifier.height(2.dp))
                            Text(item.nameAr, fontSize = 10.sp, color = Color(0xFF475569), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            1 -> {
                // Tab 1: Standard Unicode Emojis
                val emojis = listOf(
                    "😀","😂","😍","😎","😭","😅","🤣","😊","😘","🥰",
                    "😔","😢","👍","👏","🙏","💪","🔥","❤️","💔","💯",
                    "🌹","🎉","✨","⭐","😡","🤔","😴","🤯","🥳","😇",
                    "🙈","💎","👑","🇮🇶","🎵","☕","🤝","✌️","🌸","🎂"
                )
                LazyVerticalGrid(GridCells.Fixed(8), modifier = Modifier.fillMaxWidth().height(160.dp).padding(6.dp)) {
                    items(emojis.size) { i ->
                        Text(
                            emojis[i],
                            fontSize = 22.sp,
                            modifier = Modifier.padding(4.dp).clickable { onEmoji(emojis[i]) }
                        )
                    }
                }
            }
            2 -> {
                // Tab 2: GIF / Iraqi Emoticons
                if (codes.isNotEmpty()) {
                    LazyVerticalGrid(GridCells.Fixed(4), modifier = Modifier.fillMaxWidth().height(160.dp).padding(6.dp)) {
                        items(codes.size) { i ->
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF8FAFC))
                                    .clickable { onEmoticon(codes[i]) }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                coil.compose.AsyncImage(
                                    model = com.ali.textchat.ui.util.Emoticons.assetUri(codes[i]),
                                    imageLoader = loader,
                                    contentDescription = codes[i],
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text("لا توجد صور تعبيرية إضافية", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                }
            }
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
                val rankLabel = when (r.requiredRank) {
                    "OWNER" -> "👑 إدارة"
                    "MODERATOR" -> "🛡️ مراقبين"
                    "VIP" -> "⭐ مميزين"
                    "MEMBER" -> "✅ مفعلين"
                    else -> null
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (r.id == currentId) BcUserItemEnd else BcInputFill)
                        .clickable { onPick(r.id) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (r.isLocked) Icons.Default.Lock else Icons.Default.Home, null, tint = BcAccent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.title, color = Color(0xFF333333), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (rankLabel != null) {
                                Spacer(Modifier.width(6.dp))
                                Text(rankLabel, fontSize = 10.sp, color = Color(0xFFD97706),
                                    modifier = Modifier.background(Color(0xFFFEF3C7), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                        if (r.description.isNotBlank()) {
                            Text(r.description, color = Color(0xFF888888), fontSize = 11.sp)
                        }
                    }
                    Text("${r.onlineCount}", color = Color.White, fontSize = 11.sp,
                        modifier = Modifier.background(BcHeaderEnd, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileDialog(
    me: ChatUser,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (color: String?, avatar: String?, status: String?, bio: String?, age: Int?, gender: String?, country: String?) -> Unit
) {
    // Exact iqchat.top "chatbox" skin colors (css/custom.css)
    val colors = listOf("#f3d5d5", "#e9f4d4", "#d5eef5", "#e9dcee", "#f3e6d4", "#fad5f6", "#ece9ff", "#FD62BE")
    val luxuryColors = listOf("#1A1A1A", "#D4AF37", "#556B2F", "#1A237E", "#800020")
    val avatarSeeds = listOf("Iraq", "Baghdad", "Basra", "Najaf", "Karbala", "Mosul", "Kufa", "Anbar")
    var picked by remember { mutableStateOf(me.customHexColor) }
    var avatar by remember { mutableStateOf(me.avatarUrl.ifBlank { null }) }
    var bio by remember { mutableStateOf(me.bio) }
    var age by remember { mutableStateOf(me.age?.toString() ?: "") }
    var gender by remember { mutableStateOf(if (me.gender.isNotBlank()) me.gender else "ذكر") }
    var country by remember { mutableStateOf(if (me.country.isNotBlank()) me.country else "بغداد") }
    val isVip = me.rank != UserRank.REGULAR && me.rank != UserRank.BOT && !me.isGuest
    val loader = com.ali.textchat.ui.util.svgCapableLoader(androidx.compose.ui.platform.LocalContext.current)
    val scroll = rememberScrollState()

    Dialog(onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .padding(16.dp)
                .fillMaxWidth(0.95f)
                .heightIn(max = 560.dp)
                .verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(me.name, color = BcAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("${me.rank.badge} ${me.rank.titleAr}", color = Color(0xFF666666), fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))

            // Avatar picker
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("اختر صورتك الرمزية", color = Color(0xFF444444), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (!isVip) {
                    Spacer(Modifier.width(6.dp))
                    Text("🔒 خاص بـ VIP فما فوق", color = Color(0xFFC2185B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.lazy.LazyRow {
                items(avatarSeeds.size) { i ->
                    val url = "https://api.dicebear.com/7.x/bottts/png?seed=${avatarSeeds[i]}"
                    Box(
                        Modifier.padding(4.dp).size(48.dp).clip(RoundedCornerShape(6.dp))
                            .border(if (avatar == url) 3.dp else 1.dp, if (avatar == url) BcAccent else BcInputBorder, RoundedCornerShape(6.dp))
                            .clickable(enabled = isVip) { avatar = url }
                    ) {
                        coil.compose.AsyncImage(model = url, imageLoader = loader, contentDescription = null, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Bio / Status
            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 80) bio = it },
                label = { Text("الحالة / النبذة الشخصية", fontSize = 12.sp) },
                placeholder = { Text("اكتب حالتك هنا...", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            // Age and Gender
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = age,
                    onValueChange = { if (it.length <= 2 && (it.isEmpty() || it.all { c -> c.isDigit() })) age = it },
                    label = { Text("العمر", fontSize = 12.sp) },
                    placeholder = { Text("25", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(0.4f)
                )

                Column(Modifier.weight(0.6f)) {
                    Text("الجنس", fontSize = 11.sp, color = Color(0xFF666666))
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ذكر", "أنثى").forEach { g ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (gender == g) BcAccent else Color(0xFFEEEEEE))
                                    .clickable { gender = g },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(g, color = if (gender == g) Color.White else Color(0xFF333333), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // City / Country
            OutlinedTextField(
                value = country,
                onValueChange = { if (it.length <= 30) country = it },
                label = { Text("المحافظة / المدينة", fontSize = 12.sp) },
                placeholder = { Text("بغداد، البصرة، النجف...", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            // VIP Color Picker (unlocked for VIP_DIAMOND+)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("تخصيص لون الاسم والفقاعة", color = Color(0xFF444444), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (!isVip) {
                    Spacer(Modifier.width(6.dp))
                    Text("🔒 خاص بـ VIP فما فوق", color = Color(0xFFC2185B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (isVip) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    com.ali.textchat.ui.components.ColorWheel(diameter = 130.dp) { hex -> picked = hex }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape)
                                .background(picked?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: Color(0xFFECECEC))
                                .border(1.dp, Color(0xFFBDBDBD), CircleShape)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(picked ?: "—", color = Color(0xFF666666), fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Luxury Administrative Colors
                Text("👑 باقة الألوان الملكية الفاخرة:", fontSize = 11.5.sp, color = Color(0xFF856211), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val luxLabels = listOf("ملكي", "ذهبي", "زيتي", "كحلي", "عنابي")
                    luxuryColors.forEachIndexed { idx, c ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Box(
                                Modifier.size(32.dp).clip(RoundedCornerShape(6.dp))
                                    .background(Color(android.graphics.Color.parseColor(c)))
                                    .border(if (picked == c) 3.dp else 1.dp, if (picked == c) BcAccent else Color(0xFF999999), RoundedCornerShape(6.dp))
                                    .clickable { picked = c }
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(luxLabels[idx], fontSize = 10.sp, color = Color(0xFF555555))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    colors.forEach { c ->
                        Box(
                            Modifier.padding(4.dp).size(28.dp).clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(c)))
                                .border(if (picked == c) 3.dp else 0.dp, Color.Black, CircleShape)
                                .clickable { picked = c }
                        )
                    }
                }
            } else {
                Text("الألوان متاحة فقط لأصحاب رتبة VIP فما فوق لحماية هوية الشات ومنع التشتيت.", color = Color(0xFF888888), fontSize = 11.5.sp)
            }
            Spacer(Modifier.height(16.dp))

            // Save button
            Box(
                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(6.dp)).background(BcAccent)
                    .clickable {
                        onSave(if (isVip) picked else null, if (isVip) avatar else null, "online", bio, age.toIntOrNull(), gender, country)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("حفظ التعديلات", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))

            // Logout button
            Box(
                Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFFD32F2F), RoundedCornerShape(6.dp))
                    .clickable { onLogout() },
                contentAlignment = Alignment.Center
            ) {
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
    socket: ChatSocket,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit,
    onPromote: (String) -> Unit
) {
    var oldNames by remember { mutableStateOf<List<String>>(target.oldNames) }
    var linkedAccounts by remember { mutableStateOf<List<ChatUser>>(emptyList()) }
    var userIp by remember { mutableStateOf<String?>(null) }
    var loadingInspect by remember { mutableStateOf(true) }

    LaunchedEffect(target.id) {
        socket.userInspect(target.id) { ok, old, linked, ip ->
            if (ok) {
                oldNames = old
                linkedAccounts = linked
                userIp = ip
            }
            loadingInspect = false
        }
    }

    val actions = listOf(
        "unmute" to "🔊 فك الكتم / تفعيل العضو",
        "mute" to "🔇 كتم العضو",
        "ghost" to "👻 وضع الشبح (كتم صامت)",
        "unghost" to "👁️ إلغاء وضع الشبح",
        "kick" to "🚪 طرد من الغرفة",
        "ban_device" to "⛔ حظر الجهاز نهائياً",
        "delete_user" to "🗑️ حذف الحساب نهائياً"
    )

    Dialog(onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .padding(16.dp)
                .fillMaxWidth(0.92f)
                .heightIn(max = 600.dp)
        ) {
            Text("إدارة: ${target.name} (${target.rank.titleAr})", color = BcAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            val info = buildList {
                if (target.country.isNotBlank()) add("🇮🇶 ${target.country}")
                if (target.age != null) add("العمر: ${target.age}")
                if (target.gender.isNotBlank()) add(target.gender)
            }.joinToString(" • ")
            if (info.isNotBlank()) {
                Text(info, color = Color(0xFF666666), fontSize = 11.5.sp)
            }
            if (target.bio.isNotBlank()) {
                Text("“${target.bio}”", color = Color(0xFF888888), fontSize = 11.5.sp)
            }
            if (!userIp.isNullOrBlank()) {
                Text("IP: $userIp", color = Color(0xFF1976D2), fontSize = 10.5.sp)
            }
            Spacer(Modifier.height(8.dp))

            // Old Names History (Crucial client spec)
            if (oldNames.isNotEmpty()) {
                Text("📜 الأسماء السابقة: " + oldNames.joinToString(" ← "), color = Color(0xFF5D4037), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
            }

            // Linked Multi-Accounts (Crucial client spec)
            if (linkedAccounts.isNotEmpty()) {
                Text("👥 حسابات أخرى مرتبطة بالجهاز/IP (${linkedAccounts.size}):", color = Color(0xFFC2185B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    linkedAccounts.forEach { la ->
                        Text("• ${la.name} (${la.rank.badge} ${la.rank.titleAr})", fontSize = 10.5.sp, color = Color(0xFF444444))
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            Text("الرتبة والترقيات:", fontSize = 12.sp, color = Color(0xFF666666), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isOwner) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(Color(0xFFFFF3E0)).clickable { onPromote("ADMIN") }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("🌟 مدير", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100)) }
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(Color(0xFFEDE7F6)).clickable { onPromote("MODERATOR") }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("🛡️ مشرف", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF512DA8)) }
                }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(Color(0xFFE1F5FE)).clickable { onPromote("VIP_DIAMOND") }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) { Text("💎 مميز", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0288D1)) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(Color(0xFFF5F5F5)).clickable { onPromote("REGULAR") }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) { Text("👤 عادي", fontSize = 11.sp, color = Color(0xFF616161)) }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = BcInputBorder)

            LazyColumn(Modifier.fillMaxWidth()) {
                items(actions) { (a, label) ->
                    val danger = a == "ban_device" || a == "kick" || a == "delete_user"
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (a == "delete_user") Color(0xFFFFEBEE) else if (danger) Color(0xFFFDECEC) else Color(0xFFF3F3F3))
                            .clickable { onAction(a) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            label,
                            color = if (danger) Color(0xFFD32F2F) else if (a == "unmute") Color(0xFF2E7D32) else Color(0xFF333333),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var isUploading by remember { mutableStateOf(false) }

    val privImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isUploading = true
            scope.launch(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null && bytes.isNotEmpty()) {
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        socket.uploadMedia(base64, "image", "private.jpg") { ok, url ->
                            isUploading = false
                            if (ok && url != null) {
                                socket.sendPrivate(target.id, "", mediaType = "image", mediaUrl = url)
                            }
                        }
                    } else {
                        isUploading = false
                    }
                } catch (e: Exception) {
                    isUploading = false
                }
            }
        }
    }

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
            if (isUploading) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = BcAccent)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().background(BcChatBackground).padding(8.dp)) {
                items(thread) { m ->
                    val isOther = m.senderId == target.id
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalAlignment = if (isOther) Alignment.Start else Alignment.End
                    ) {
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(if (isOther) Color(0xFFE2E8F0) else Color(0xFFFCE7F3))
                                .padding(8.dp)
                        ) {
                            Column {
                                if (m.mediaType == "image" && !m.mediaUrl.isNullOrBlank()) {
                                    val fullUrl = if (m.mediaUrl.startsWith("http")) m.mediaUrl else "${com.ali.textchat.data.AppConfig.SERVER_URL}${m.mediaUrl}"
                                    coil.compose.AsyncImage(
                                        model = fullUrl,
                                        contentDescription = "صورة خاصة",
                                        modifier = Modifier.sizeIn(maxWidth = 180.dp, maxHeight = 180.dp).clip(RoundedCornerShape(6.dp))
                                    )
                                    Spacer(Modifier.height(4.dp))
                                }
                                if (m.text.isNotBlank() && m.text != "📷 صورة" && m.text != "📷 صورة خاصة") {
                                    Text(m.text, color = Color(0xFF1E293B), fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().background(Color.White).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).clickable { privImagePicker.launch("image/*") }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PhotoCamera, "إرسال صورة", tint = BcAccent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(4.dp))
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
