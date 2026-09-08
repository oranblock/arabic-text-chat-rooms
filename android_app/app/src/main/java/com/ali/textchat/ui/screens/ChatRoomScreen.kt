package com.ali.textchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ali.textchat.model.*
import com.ali.textchat.ui.components.*
import com.ali.textchat.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    currentRoom: ChatRoom,
    currentUser: ChatUser,
    onSwitchRoom: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenPrivateMessages: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var inputMessageText by remember { mutableStateOf("") }
    var isYtPlayerVisible by remember { mutableStateOf(true) }

    // Mock initial messages conforming to client specifications
    var messages by remember {
        mutableStateOf(
            listOf(
                ChatMessage(
                    id = "m1",
                    roomId = currentRoom.id,
                    senderId = "u_owner",
                    senderName = "علي (المالك)",
                    senderAvatar = "https://api.dicebear.com/7.x/bottts/svg?seed=AdminAli",
                    senderRank = UserRank.OWNER,
                    text = "أهلاً وسهلاً بجميع الإخوة الكرام في الغرفة، نرجو الالتزام بقواعد الشات والاحترام المتبادل 🌹",
                    timestamp = "14:32"
                ),
                ChatMessage(
                    id = "m2",
                    roomId = currentRoom.id,
                    senderId = "u_mod",
                    senderName = "سامي (مشرف عام)",
                    senderAvatar = "https://api.dicebear.com/7.x/bottts/svg?seed=ModSami",
                    senderRank = UserRank.MODERATOR,
                    text = "تذكير: الرسائل المتكررة والإعلانات تُعرّض صاحبها لكتم فوري وحظر العتاد Hardware Ban 🔒",
                    timestamp = "14:34"
                ),
                ChatMessage(
                    id = "m3",
                    roomId = currentRoom.id,
                    senderId = "u_vip",
                    senderName = "فهد التميمي",
                    senderAvatar = "https://api.dicebear.com/7.x/bottts/svg?seed=FahadVIP",
                    senderRank = UserRank.VIP_DIAMOND,
                    customHexColor = "#059669",
                    text = "مساء الخير للجميع، كيف الصوت واليوتيوب عندكم اليوم؟",
                    timestamp = "14:36"
                )
            )
        )
    }

    val onlineUsers = remember {
        listOf(
            ChatUser(id = "u_owner", name = "علي (المالك)", avatarUrl = "https://api.dicebear.com/7.x/bottts/svg?seed=AdminAli", rank = UserRank.OWNER, deviceId = "hw_1"),
            ChatUser(id = "u_mod", name = "سامي (مشرف)", avatarUrl = "https://api.dicebear.com/7.x/bottts/svg?seed=ModSami", rank = UserRank.MODERATOR, deviceId = "hw_2"),
            ChatUser(id = "u_vip", name = "فهد التميمي", avatarUrl = "https://api.dicebear.com/7.x/bottts/svg?seed=FahadVIP", rank = UserRank.VIP_DIAMOND, deviceId = "hw_3"),
            ChatUser(id = "u_reg", name = "خالد الحربي", avatarUrl = "https://api.dicebear.com/7.x/bottts/svg?seed=KhalidUser", rank = UserRank.REGULAR, deviceId = "hw_4")
        )
    }

    val listState = rememberLazyListState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            OnlineUsersDrawer(
                users = onlineUsers,
                onUserClick = { user ->
                    // Quick mention from drawer
                    inputMessageText = "@${user.name}: $inputMessageText"
                    scope.launch { drawerState.close() }
                },
                onClose = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            topBar = {
                // Requirement 1: Royal Blue Top Bar with 5 icons
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = currentRoom.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${onlineUsers.size} متصل الآن",
                                fontSize = 11.sp,
                                color = Color(0xFFBFDBFE)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "قائمة المتصلين",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        // 1. Notifications
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.Notifications, contentDescription = "التنبيهات", tint = Color.White)
                        }
                        // 2. Room Switcher
                        IconButton(onClick = onSwitchRoom) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "الغرف", tint = Color.White)
                        }
                        // 3. Private Mail
                        IconButton(onClick = onOpenPrivateMessages) {
                            Icon(Icons.Default.Email, contentDescription = "الخاص", tint = Color.White)
                        }
                        // 4. Friend Requests
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.Person, contentDescription = "الطلبات", tint = Color.White)
                        }
                        // 5. Profile
                        IconButton(onClick = onOpenProfile) {
                            Icon(Icons.Default.AccountCircle, contentDescription = "الملف الشخصي", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = RoyalBluePrimary
                    )
                )
            },
            bottomBar = {
                // Bottom Input Bar
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { inputMessageText += " 🌹 " }) {
                            Icon(Icons.Default.Favorite, contentDescription = "إيموجي", tint = Color.Gray)
                        }

                        TextField(
                            value = inputMessageText,
                            onValueChange = { inputMessageText = it },
                            placeholder = { Text("اكتب رسالتك هنا...", fontSize = 13.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 46.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = Color(0xFFF1F5F9),
                                unfocusedContainerColor = Color(0xFFF1F5F9)
                            )
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = {
                                if (inputMessageText.isNotBlank()) {
                                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                    val newMsg = ChatMessage(
                                        id = UUID.randomUUID().toString(),
                                        roomId = currentRoom.id,
                                        senderId = currentUser.id,
                                        senderName = currentUser.name,
                                        senderAvatar = currentUser.avatarUrl,
                                        senderRank = currentUser.rank,
                                        customHexColor = currentUser.customHexColor,
                                        text = inputMessageText.trim(),
                                        timestamp = time
                                    )
                                    messages = messages + newMsg
                                    inputMessageText = ""
                                    scope.launch {
                                        listState.animateScrollToItem(messages.size - 1)
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(RoyalBluePrimary, CircleShape)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "إرسال", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(ChatBackground)
            ) {
                // In-Chat Floating YouTube Player (Requirement 6)
                if (isYtPlayerVisible) {
                    YouTubeInChatPlayer(
                        videoTitle = "شيلات & موسيقى ديوانية العرب Live 🎵",
                        onClose = { isYtPlayerVisible = false }
                    )
                }

                // Chat Messages List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(messages) { msg ->
                        MessageBubble(
                            message = msg,
                            onUserMention = { targetUser ->
                                // Quick Mention On-Tap (Requirement 4)
                                inputMessageText = "@${targetUser}: $inputMessageText"
                            }
                        )
                    }
                }
            }
        }
    }
}
