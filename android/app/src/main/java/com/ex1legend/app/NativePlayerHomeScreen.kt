package com.ex1legend.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * First native parity slice for app.html's player home page.
 *
 * This is deliberately a standalone Compose screen. It does not load HTML,
 * WebView, JavaScript, or copy web implementation code into the native layer.
 * All callbacks are supplied by the eventual native navigation/data layer.
 */
private val Ex1Bg = Color(0xFF090817)
private val Ex1Surface = Color(0xFF17152A)
private val Ex1Surface2 = Color(0xFF211D38)
private val Ex1Stroke = Color(0xFF403957)
private val Ex1Text = Color(0xFFF8F6FF)
private val Ex1Muted = Color(0xFFAAA4C1)
private val Ex1Violet = Color(0xFF9A4DE8)
private val Ex1Cyan = Color(0xFF28C4D4)

@Composable
fun NativePlayerHomeScreen(
    onSearchTournaments: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAuth: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenTournaments: () -> Unit,
    onOpenRanking: () -> Unit,
    onOpenMessages: () -> Unit,
    onSearchPlayers: (String) -> Unit
) {
    var homeTab by remember { mutableStateOf("explore") }
    var playerQuery by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize(), color = Ex1Bg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x556425A8), Ex1Bg),
                        radius = 1050f
                    )
                )
        ) {
            PlayerHeader(
                onSearchTournaments = onSearchTournaments,
                onOpenSettings = onOpenSettings,
                onOpenAuth = onOpenAuth
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 17.dp)
            ) {
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "EX1 LEGEND COMMUNITY",
                            color = Color(0xFFD3A7FF),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                        Text(
                            "میدان بازیکنان",
                            color = Ex1Text,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    SmallOutlineAction("پروفایل من", onOpenProfile)
                }

                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Ex1Surface, RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    HomeTabButton(
                        text = "اکسپلور بازیکنان",
                        selected = homeTab == "explore",
                        modifier = Modifier.weight(1f)
                    ) { homeTab = "explore" }
                    HomeTabButton(
                        text = "چت",
                        selected = homeTab == "messages",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            homeTab = "messages"
                            onOpenMessages()
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    ShortcutCard(
                        title = "مسابقات",
                        subtitle = "روم‌های فعال",
                        mark = "♜",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenTournaments
                    )
                    ShortcutCard(
                        title = "قهرمانان",
                        subtitle = "رتبه‌ها",
                        mark = "✦",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenRanking
                    )
                    ShortcutCard(
                        title = "پروفایل",
                        subtitle = "اطلاعات من",
                        mark = "◈",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenProfile
                    )
                }

                Spacer(Modifier.height(22.dp))
                Text(
                    if (homeTab == "explore") "اکسپلور بازیکنان" else "گفت‌وگوها",
                    color = Ex1Text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (homeTab == "explore") "بازیکن‌های جامعه EX1 را پیدا کن"
                    else "پیام فقط بین بازیکن‌هایی که دوطرفه همدیگر را دنبال می‌کنند فعال است.",
                    color = Ex1Muted,
                    fontSize = 11.sp,
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(12.dp))

                if (homeTab == "explore") {
                    OutlinedTextField(
                        value = playerQuery,
                        onValueChange = { playerQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("جست‌وجوی نام بازیکن...", color = Ex1Muted) },
                        trailingIcon = {
                            Button(
                                onClick = { onSearchPlayers(playerQuery) },
                                colors = ButtonDefaults.buttonColors(containerColor = Ex1Violet)
                            ) { Text("جست‌وجو") }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Ex1Text,
                            unfocusedTextColor = Ex1Text,
                            focusedBorderColor = Ex1Violet,
                            unfocusedBorderColor = Ex1Stroke,
                            cursorColor = Ex1Cyan
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    EmptyStateCard("برای جست‌وجو وارد حساب شو.")
                } else {
                    EmptyStateCard("برای شروع چت وارد حساب شو.")
                }
                Spacer(Modifier.height(28.dp))
            }

            PlayerBottomNavigation(
                onHome = {},
                onTournaments = onOpenTournaments,
                onRanking = onOpenRanking,
                onProfile = onOpenProfile
            )
        }
    }
}

@Composable
private fun PlayerHeader(
    onSearchTournaments: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAuth: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xEF0A0818))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            HeaderIcon(Icons.Outlined.Search, "جست‌وجو", onSearchTournaments)
            HeaderIcon(Icons.Outlined.Wifi, "وضعیت اتصال") {}
        }
        Text(
            "EX1 LEGEND",
            color = Ex1Text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.6).sp
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            HeaderIcon(Icons.Outlined.Settings, "تنظیمات", onOpenSettings)
            SmallOutlineAction("ورود", onOpenAuth)
        }
    }
}

@Composable
private fun HeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(38.dp).border(androidx.compose.foundation.BorderStroke(1.dp, Ex1Stroke), RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        color = Color(0xFF151226),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = Color(0xFFCFC5E2), modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun SmallOutlineAction(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.border(androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF59466F)), RoundedCornerShape(11.dp)).clickable(onClick = onClick),
        color = Color(0xFF29223D),
        shape = RoundedCornerShape(11.dp),
    ) {
        Text(text, color = Ex1Text, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp))
    }
}

@Composable
private fun HomeTabButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) Color(0xFF3B2857) else Color.Transparent,
        shape = RoundedCornerShape(11.dp)
    ) {
        Text(
            text,
            color = if (selected) Ex1Text else Ex1Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp)
        )
    }
}

@Composable
private fun ShortcutCard(
    title: String,
    subtitle: String,
    mark: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Ex1Surface2),
        shape = RoundedCornerShape(15.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33403957))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(mark, color = Color(0xFFD5A4FF), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(title, color = Ex1Text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Ex1Muted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().border(androidx.compose.foundation.BorderStroke(1.dp, Color(0x55625176)), RoundedCornerShape(16.dp)),
        color = Color(0x99211A34),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(message, color = Color(0xFFB8AECb), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(22.dp))
    }
}

@Composable
private fun PlayerBottomNavigation(
    onHome: () -> Unit,
    onTournaments: () -> Unit,
    onRanking: () -> Unit,
    onProfile: () -> Unit
) {
    NavigationBar(containerColor = Color(0xEF111020), contentColor = Color(0xFFD5A4FF)) {
        NavigationBarItem(selected = true, onClick = onHome, icon = { Icon(Icons.Outlined.Home, null) }, label = { Text("خانه", fontSize = 9.sp) })
        NavigationBarItem(selected = false, onClick = onTournaments, icon = { Icon(Icons.Outlined.EmojiEvents, null) }, label = { Text("مسابقات", fontSize = 9.sp) })
        NavigationBarItem(selected = false, onClick = onRanking, icon = { Icon(Icons.Outlined.Star, null) }, label = { Text("قهرمانان", fontSize = 9.sp) })
        NavigationBarItem(selected = false, onClick = onProfile, icon = { Icon(Icons.Outlined.Person, null) }, label = { Text("پروفایل", fontSize = 9.sp) })
    }
}
