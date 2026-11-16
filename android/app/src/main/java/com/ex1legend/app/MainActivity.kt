package com.ex1legend.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Night = Color(0xFF090711)
private val Panel = Color(0xFF171323)
private val Violet = Color(0xFF9B5CFF)
private val Muted = Color(0xFFAAA4B9)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 7, 17)
        window.navigationBarColor = android.graphics.Color.rgb(9, 7, 17)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Violet, background = Night, surface = Panel)) {
                if (BuildConfig.IS_ADMIN) AdminApp() else PlayerApp()
            }
        }
    }
}

@Composable
private fun PlayerApp() {
    var selected by remember { mutableIntStateOf(0) }
    var authenticated by remember { mutableStateOf(false) }
    if (!authenticated) {
        AuthScreen(onSubmit = { /* Supabase Auth is intentionally not simulated; integration is next. */ })
        return
    }
    Scaffold(
        containerColor = Night,
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                listOf("روم‌ها", "استارها", "پروفایل").forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = selected == i,
                        onClick = { selected = i },
                        icon = { Icon(if (i == 0) Icons.Default.Home else if (i == 1) Icons.Default.Star else Icons.Default.Person, label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (selected) {
                0 -> { Heading("روم‌های مسابقه", "روم‌های فعال و برنامه‌ریزی‌شده"); InfoCard("فهرست روم‌ها", "پس از اتصال امن به سرور، روم‌ها و وضعیت هرکدام اینجا نمایش داده می‌شوند.") }
                1 -> { Heading("کیف استار", "امتیازهای داخلی و غیرنقدی EX1"); InfoCard("موجودی استار", "موجودی و تاریخچه پس از دریافت اطلاعات حساب نمایش داده می‌شود.") }
                else -> {
                    Heading("پروفایل بازیکن", "حساب و فعالیت‌های شما")
                    InfoCard("پروفایل EX1", "نام نمایشی، شناسه، سوابق روم‌ها و اطلاعات تیم در این بخش قرار می‌گیرد.")
                    OutlinedButton(onClick = { authenticated = false }, modifier = Modifier.fillMaxWidth()) { Text("خروج از حساب") }
                }
            }
        }
    }
}

@Composable
private fun AuthScreen(onSubmit: () -> Unit) {
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF24143D), Night, Night)))) {
        Column(Modifier.fillMaxWidth().align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(78.dp).background(Violet.copy(alpha = .18f), CircleShape), contentAlignment = Alignment.Center) {
                Text("EX1", fontSize = 27.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(16.dp))
            Text("EX1 LEGEND", fontSize = 27.sp, fontWeight = FontWeight.Black)
            Text("دنیای رقابت و بازی", color = Muted)
            Spacer(Modifier.height(26.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (register) "ساخت حساب کاربری" else "ورود به حساب", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (register) Field(displayName, { displayName = it }, "نام نمایشی")
                    Field(email, { email = it }, "ایمیل", KeyboardType.Email)
                    Field(password, { password = it }, "رمز عبور", password = true)
                    Button(onClick = onSubmit, enabled = false, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        Text(if (register) "ثبت‌نام (در دست پیاده‌سازی)" else "ورود (در دست پیاده‌سازی)")
                    }
                    Text(if (register) "حساب داری؟ ورود" else "حساب نداری؟ ثبت‌نام",
                        Modifier.fillMaxWidth().clickable { register = !register }.padding(8.dp),
                        color = Violet, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("استارها امتیاز داخلی و غیرنقدی EX1 هستند.", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Field(value: String, change: (String) -> Unit, label: String, keyboard: KeyboardType = KeyboardType.Text, password: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = change, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
    )
}

@Composable
private fun AdminApp() {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(containerColor = Night, bottomBar = {
        NavigationBar(containerColor = Panel) {
            listOf("روم‌ها", "بازیکنان", "مالک").forEachIndexed { i, label ->
                NavigationBarItem(tab == i, { tab = i }, icon = { Icon(if (i == 0) Icons.Default.Home else Icons.Default.Person, label) }, label = { Text(label) })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Heading("پنل مدیریت EX1", "نسخهٔ بومی مدیریت")
            InfoCard(listOf("مدیریت روم‌ها", "مدیریت بازیکنان", "بخش مالک")[tab],
                "این بخش فقط پس از احراز هویت و بررسی مجوزهای سرور فعال می‌شود. در این پیش‌نمایش هیچ عملیات مدیریتی اجرا نمی‌شود.")
        }
    }
}

@Composable
private fun Heading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}
