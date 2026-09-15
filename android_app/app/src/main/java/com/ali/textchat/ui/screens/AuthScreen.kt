package com.ali.textchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ali.textchat.ui.theme.*

/** Login / register / guest entry, styled like the site's magenta login card. */
@Composable
fun AuthScreen(
    busy: Boolean,
    error: String?,
    onLogin: (name: String, password: String) -> Unit,
    onRegister: (name: String, password: String) -> Unit,
    onGuest: () -> Unit
) {
    var isRegister by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BcHeaderStart, BcHeaderEnd, BcChatBackground))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🇮🇶", fontSize = 44.sp)
            Text("ديوانية العراق", color = BcAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("دردشة عراقية جماعية", color = Color(0xFF888888), fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))

            AuthField(name, "الاسم") { name = it }
            Spacer(Modifier.height(10.dp))
            AuthField(password, "الرمز السري", isPassword = true) { password = it }

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error, color = Color(0xFFD32F2F), fontSize = 12.sp, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(16.dp))
            PrimaryButton(if (isRegister) "إنشاء حساب" else "تسجيل الدخول", busy) {
                if (name.isNotBlank() && password.isNotBlank()) {
                    if (isRegister) onRegister(name.trim(), password) else onLogin(name.trim(), password)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (isRegister) "لديك حساب؟ سجل الدخول" else "عضو جديد؟ أنشئ حساباً",
                color = BcAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { isRegister = !isRegister }
            )
            Spacer(Modifier.height(6.dp))
            Text("دخول كزائر", color = Color(0xFF666666), fontSize = 13.sp,
                modifier = Modifier.clickable { if (!busy) onGuest() })
        }
    }
}

@Composable
private fun AuthField(value: String, hint: String, isPassword: Boolean = false, onValueChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(BcInputFill, RoundedCornerShape(6.dp))
            .border(1.dp, BcInputBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) Text(hint, color = Color(0xFF9E9E9E), fontSize = 14.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Color(0xFF181818), fontSize = 15.sp),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PrimaryButton(label: String, busy: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(BcAccent)
            .clickable(enabled = !busy) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        else Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
