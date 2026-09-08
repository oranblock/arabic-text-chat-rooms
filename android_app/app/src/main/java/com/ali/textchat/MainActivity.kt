package com.ali.textchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ali.textchat.model.ChatRoom
import com.ali.textchat.model.ChatUser
import com.ali.textchat.model.UserRank
import com.ali.textchat.ui.screens.ChatRoomScreen
import com.ali.textchat.ui.theme.ArabicTextChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ArabicTextChatTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val defaultRoom = ChatRoom(
                        id = "room_arab_diwan",
                        title = "غرفة ديوانية العرب",
                        description = "غرفة عامة للأحاديث واللقاءات الودية",
                        onlineCount = 142
                    )

                    val currentUser = ChatUser(
                        id = "user_me",
                        name = "أحمد",
                        avatarUrl = "https://api.dicebear.com/7.x/bottts/svg?seed=UserMe",
                        rank = UserRank.VIP_DIAMOND,
                        customHexColor = "#0284C7",
                        deviceId = "demo_hardware_uuid"
                    )

                    ChatRoomScreen(
                        currentRoom = defaultRoom,
                        currentUser = currentUser,
                        onSwitchRoom = { /* Navigate to rooms list */ },
                        onOpenProfile = { /* Open user profile */ },
                        onOpenPrivateMessages = { /* Open private chat */ }
                    )
                }
            }
        }
    }
}
