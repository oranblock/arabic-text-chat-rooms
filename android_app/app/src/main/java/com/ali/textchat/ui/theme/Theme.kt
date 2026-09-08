package com.ali.textchat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = RoyalBluePrimary,
    secondary = DiamondCyan,
    tertiary = OwnerGold,
    background = ChatBackground,
    surface = MessageBubbleBackground,
    onPrimary = Color.White,
    onBackground = RegularUserText
)

@Composable
fun ArabicTextChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
