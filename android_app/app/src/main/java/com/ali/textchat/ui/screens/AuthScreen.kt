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
    serverUrl: String,
    onUpdateServerUrl: (String) -> Unit,
    onLogin: (name: String, password: String) -> Unit,
    onRegister: (name: String, password: String, age: String, gender: String, country: String, status: String) -> Unit,
    onGuest: () -> Unit
) {
    var isRegister by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("male") }
    var country by remember { mutableStateOf("العراق") }
    var status by remember { mutableStateOf("") }
    var showServerDialog by remember { mutableStateOf(false) }
    var tempUrl by remember(serverUrl) { mutableStateOf(serverUrl) }

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

            if (isRegister) {
                Spacer(Modifier.height(10.dp))
                AuthField(age, "العمر") { if (it.length <= 2 && it.all { c -> c.isDigit() }) age = it }
                Spacer(Modifier.height(10.dp))
                AuthField(country, "البلد") { country = it }
                Spacer(Modifier.height(10.dp))
                AuthField(status, "الحالة (اختياري)") { status = it }
                Spacer(Modifier.height(10.dp))
                Text("نوع الجنس", color = Color(0xFF888888), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GenderChip("ذكر", gender == "male", Modifier.weight(1f)) { gender = "male" }
                    GenderChip("أنثى", gender == "female", Modifier.weight(1f)) { gender = "female" }
                    GenderChip("آخر", gender == "other", Modifier.weight(1f)) { gender = "other" }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error, color = Color(0xFFD32F2F), fontSize = 12.sp, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(16.dp))
            PrimaryButton(if (isRegister) "إنشاء حساب" else "تسجيل الدخول", busy) {
                if (name.isNotBlank() && password.isNotBlank()) {
                    if (isRegister) onRegister(name.trim(), password, age, gender, country.trim(), status.trim()) else onLogin(name.trim(), password)
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

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { showServerDialog = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚙️", fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Text("إعدادات سيرفر الربط", color = Color(0xFF888888), fontSize = 11.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
            }
        }
    }

    if (showServerDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = {
                Text("🌐 إعدادات سيرفر الدردشة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column {
                    Text(
                        "السيرفر متصل سحابياً للربط المباشر بين جهازين في أي مكان. يمكنك تغيير العنوان إذا أردت ربط شبكة محلية:",
                        fontSize = 12.sp,
                        color = Color(0xFF555555),
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    AuthField(value = tempUrl, hint = "https://...") { tempUrl = it }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "استعادة السيرفر السحابي الافتراضي ↺",
                        color = BcAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            tempUrl = com.ali.textchat.data.AppConfig.DEFAULT_SERVER_URL
                        }
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (tempUrl.isNotBlank()) {
                            onUpdateServerUrl(tempUrl.trim())
                        }
                        showServerDialog = false
                    }
                ) {
                    Text("حفظ واتصال", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showServerDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
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
private fun GenderChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) BcAccent else BcInputFill)
            .border(1.dp, if (selected) BcAccent else BcInputBorder, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.White else Color(0xFF555555), fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
