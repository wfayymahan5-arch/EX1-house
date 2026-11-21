package com.ex1legend.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class NativePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(10, 8, 18)
        window.navigationBarColor = android.graphics.Color.rgb(10, 8, 18)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                    primary = Color(0xFFB58CFF),
                    secondary = Color(0xFF8D65D6),
                    background = Color(0xFF0A0812),
                    surface = Color(0xFF171322),
                    onSurface = Color(0xFFF4F0FA)
                )
            ) {
                    NativePreviewRoot(isAdmin = BuildConfig.IS_ADMIN)
                }
            }
        }
    }
}

@Composable
private fun NativePreviewRoot(isAdmin: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf(NativeEx1Api.loadSession(context)) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var rooms by remember { mutableStateOf<List<NativeRoom>>(emptyList()) }
    var profile by remember { mutableStateOf<NativeProfile?>(null) }
    var wallet by remember { mutableStateOf<NativeWallet?>(null) }
    var activeTab by remember { mutableStateOf("rooms") }
    var permissions by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(session != null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf("all") }
    var price by remember { mutableStateOf("all") }
    var confirmSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(session?.userId) {
        val current = session ?: return@LaunchedEffect
        loading = true
        error = null
        try {
            val renewed = NativeEx1Api.refresh(current)
            NativeEx1Api.saveSession(context, renewed)
            session = renewed
            if (isAdmin) permissions = NativeEx1Api.adminPermissions(renewed)
            else rooms = NativeEx1Api.rooms(renewed)
        } catch (e: NativeApiException) {
            if (e.status == 400 || e.status == 401 || e.status == 403) {
                NativeEx1Api.clearSession(context)
                session = null
                error = "نشست قبلی معتبر نیست؛ دوباره وارد شو."
            } else {
                error = e.message ?: "ارتباط با سرور برقرار نشد."
            }
        } catch (e: Exception) {
            error = e.message ?: "فعلاً دریافت اطلاعات ممکن نیست."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(session?.userId, activeTab) {
        val current = session ?: return@LaunchedEffect
        if (!isAdmin && activeTab != "rooms") {
            loading = true
            try {
                if (activeTab == "profile") profile = NativeEx1Api.profile(current)
                if (activeTab == "wallet") wallet = NativeEx1Api.wallet(current)
                error = null
            } catch (e: Exception) {
                error = e.message ?: "دریافت اطلاعات انجام نشد."
            } finally {
                loading = false
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0812)) {
        if (session == null) {
            LoginPanel(
                email = email,
                password = password,
                displayName = displayName,
                isSignUp = isSignUp,
                busy = busy,
                error = error,
                onEmail = { email = it },
                onPassword = { password = it },
                onDisplayName = { displayName = it },
                onModeSwitch = { isSignUp = !isSignUp; error = null },
                onForgotPassword = {
                    if (email.isBlank()) {
                        error = "اول ایمیل حساب را وارد کن."
                    } else {
                        scope.launch {
                            busy = true
                            error = null
                            try {
                                NativeEx1Api.requestPasswordReset(email)
                                error = "اگر این ایمیل ثبت شده باشد، لینک بازیابی برایت ارسال می‌شود. ایمیلت را بررسی کن."
                            } catch (e: Exception) {
                                error = e.message ?: "ارسال لینک بازیابی انجام نشد."
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                onSubmit = {
                    if (email.isBlank() || password.length < 8 || (isSignUp && displayName.trim().length < 2)) {
                        error = if (isSignUp) "نام نمایشی حداقل ۲ نویسه و رمز عبور حداقل ۸ نویسه باشد."
                        else "ایمیل را وارد کن و رمز عبور باید حداقل ۸ نویسه باشد."
                    } else {
                        scope.launch {
                            busy = true
                            error = null
                            try {
                                if (isSignUp) {
                                    val created = NativeEx1Api.signUp(email, password, displayName)
                                    if (created == null) {
                                        error = "درخواست ساخت حساب ثبت شد؛ برای تکمیل ثبت‌نام ایمیلت را تأیید کن."
                                    } else {
                                        NativeEx1Api.saveSession(context, created)
                                        session = created
                                    }
                                } else {
                                    val loggedIn = NativeEx1Api.signIn(email, password)
                                    NativeEx1Api.saveSession(context, loggedIn)
                                    session = loggedIn
                                }
                            } catch (e: Exception) {
                                error = e.message ?: "ورود یا ثبت‌نام انجام نشد."
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                isAdmin = isAdmin
            )
        } else if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (isAdmin) {
            AdminPreviewPanel(
                permissions = permissions,
                error = error,
                onRefresh = {
                    scope.launch {
                        loading = true
                        try {
                            permissions = NativeEx1Api.adminPermissions(session!!)
                            error = null
                        } catch (e: Exception) {
                            error = e.message ?: "دریافت مجوزها ناموفق بود."
                        } finally {
                            loading = false
                        }
                    }
                },
                onSignOut = { confirmSignOut = true }
            )
        } else {
            PlayerPreviewPanel(
                rooms = rooms,
                profile = profile,
                wallet = wallet,
                activeTab = activeTab,
                onTab = { activeTab = it },
                onSaveProfile = { name, username, uid, gameName ->
                    scope.launch {
                        val current = session ?: return@launch
                        loading = true
                        try {
                            val result = NativeEx1Api.updateProfile(current, name, username, uid, gameName)
                            if (result.optBoolean("pending_review")) {
                                error = result.optString("message", "پروفایل برای بررسی ارسال شد.")
                            } else if (result.optBoolean("success")) {
                                profile = NativeEx1Api.profile(current)
                                error = "پروفایل ذخیره شد."
                            } else {
                                error = result.optString("error", "ذخیره پروفایل انجام نشد.")
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "ذخیره پروفایل انجام نشد."
                        } finally {
                            loading = false
                        }
                    }
                },
                category = category,
                price = price,
                error = error,
                onCategory = { category = it },
                onPrice = { price = it },
                onRefresh = {
                    scope.launch {
                        loading = true
                        try {
                            val current = session ?: return@launch
                            when (activeTab) {
                                "profile" -> profile = NativeEx1Api.profile(current)
                                "wallet" -> wallet = NativeEx1Api.wallet(current)
                                else -> rooms = NativeEx1Api.rooms(current)
                            }
                            error = null
                        } catch (e: Exception) {
                            error = e.message ?: "دریافت اطلاعات ناموفق بود."
                        } finally {
                            loading = false
                        }
                    }
                },
                onSignOut = { confirmSignOut = true }
            )
        }
        if (confirmSignOut) {
            AlertDialog(
                onDismissRequest = { confirmSignOut = false },
                title = { Text("خروج از حساب") },
                text = { Text("آیا می‌خواهید از حساب کاربری خود خارج شوید؟") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmSignOut = false
                        scope.launch {
                            val current = session
                            if (current != null) runCatching { NativeEx1Api.signOut(current) }
                            NativeEx1Api.clearSession(context)
                            session = null
                        }
                    }) { Text("خروج") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmSignOut = false }) { Text("انصراف") }
                }
            )
        }
    }
}

@Composable
private fun LoginPanel(
    email: String,
    password: String,
    displayName: String,
    isSignUp: Boolean,
    busy: Boolean,
    error: String?,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onDisplayName: (String) -> Unit,
    onModeSwitch: () -> Unit,
    onForgotPassword: () -> Unit,
    onSubmit: () -> Unit,
    isAdmin: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("EX1 LEGEND", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
        Text(if (isAdmin) "پنل مدیریت · نسخه Native آزمایشی" else "وارد میدان شو، افسانه شو", color = Color(0xFFBBA8D8))
        Spacer(Modifier.height(28.dp))
        if (isSignUp) {
            OutlinedTextField(value = displayName, onValueChange = onDisplayName, label = { Text("نام نمایشی") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(value = email, onValueChange = onEmail, label = { Text("ایمیل") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = password, onValueChange = onPassword, label = { Text("رمز عبور (حداقل ۸ نویسه)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))
        Button(onClick = onSubmit, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            if (busy) CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
            else Text(if (isSignUp) "ساخت حساب" else "ورود امن")
        }
        TextButton(onClick = onModeSwitch, enabled = !busy) {
            Text(if (isSignUp) "قبلاً حساب داری؟ ورود" else "ساخت حساب جدید")
        }
        if (!isSignUp) TextButton(onClick = onForgotPassword, enabled = !busy) {
            Text("فراموشی رمز عبور")
        }
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = if (error.contains("ارسال شد") || error.contains("تأیید کن")) Color(0xFFBFA0F4) else Color(0xFFFF9B9B), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(18.dp))
        Text("ورود و ساخت حساب از طریق Supabase Auth انجام می‌شود.", color = Color(0xFF8E879B), fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PlayerPreviewPanel(
    rooms: List<NativeRoom>,
    profile: NativeProfile?,
    wallet: NativeWallet?,
    activeTab: String,
    onTab: (String) -> Unit,
    onSaveProfile: (String, String, String, String) -> Unit,
    category: String,
    price: String,
    error: String?,
    onCategory: (String) -> Unit,
    onPrice: (String) -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("EX1 LEGEND", fontWeight = FontWeight.Black, fontSize = 23.sp)
                Text("نسخه Native · پیش‌نمایش بازیکن", color = Color(0xFFBBA8D8), fontSize = 12.sp)
            }
            Button(onClick = onRefresh) { Text("به‌روزرسانی") }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(selected = activeTab == "rooms", onClick = { onTab("rooms") }, label = { Text("روم‌ها") })
            FilterChip(selected = activeTab == "profile", onClick = { onTab("profile") }, label = { Text("پروفایل") })
            FilterChip(selected = activeTab == "wallet", onClick = { onTab("wallet") }, label = { Text("کیف پول مجازی") })
        }
        if (!error.isNullOrBlank()) Text(error, color = if (error.contains("ذخیره شد") || error.contains("بررسی ارسال")) Color(0xFFBFA0F4) else Color(0xFFFFA2A2), modifier = Modifier.padding(vertical = 8.dp))
        when (activeTab) {
            "profile" -> ProfilePreviewPanel(profile = profile, onSave = onSaveProfile)
            "wallet" -> WalletPreviewPanel(wallet = wallet)
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    listOf("all" to "برای شما", "battle_royale" to "بتل‌رویال", "fight" to "فایت", "multiplayer" to "مولتی", "uncategorized" to "بدون دسته").forEach { (key, label) ->
                        FilterChip(selected = category == key, onClick = { onCategory(key) }, label = { Text(label, fontSize = 11.sp) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    listOf("all" to "همه قیمت‌ها", "free" to "رایگان", "economy" to "اقتصادی", "expensive" to "گران").forEach { (key, label) ->
                        FilterChip(selected = price == key, onClick = { onPrice(key) }, label = { Text(label, fontSize = 11.sp) })
                    }
                }
                val visibleRooms = rooms.filter {
                    (category == "all" || it.gameCategory == category) &&
                        (price == "all" || it.priceCategory == price)
                }
                if (visibleRooms.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("در این دسته روم فعالی پیدا نشد.", color = Color(0xFFAAA2B8))
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                        items(visibleRooms, key = { it.id }) { room -> RoomPreviewCard(room) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("خروج از حساب") }
    }
}

@Composable
private fun ProfilePreviewPanel(
    profile: NativeProfile?,
    onSave: (String, String, String, String) -> Unit
) {
    var displayName by remember(profile?.id, profile?.displayName) { mutableStateOf(profile?.displayName.orEmpty()) }
    var username by remember(profile?.id, profile?.username) { mutableStateOf(profile?.username.orEmpty()) }
    var gameUid by remember(profile?.id, profile?.gameUid) { mutableStateOf(profile?.gameUid.orEmpty()) }
    var gameName by remember(profile?.id, profile?.gameName) { mutableStateOf(profile?.gameName.orEmpty()) }
    Column(
        Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("اطلاعات پروفایل", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        OutlinedTextField(displayName, { displayName = it }, label = { Text("نام نمایشی") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(username, { username = it }, label = { Text("نام کاربری") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(gameUid, { gameUid = it }, label = { Text("UID کالاف") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(gameName, { gameName = it }, label = { Text("نام داخل بازی") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text("تغییرات از مسیر امن سرور بررسی می‌شوند؛ ممکن است بعضی تغییرها نیازمند تأیید مالک باشند.", color = Color(0xFF9B91AA), fontSize = 12.sp)
        Button(
            onClick = { onSave(displayName, username, gameUid, gameName) },
            enabled = displayName.trim().length >= 2,
            modifier = Modifier.fillMaxWidth()
        ) { Text("ذخیره پروفایل") }
    }
}

@Composable
private fun WalletPreviewPanel(wallet: NativeWallet?) {
    Column(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
        Text("کیف پول مجازی", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF211831))) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("موجودی امتیاز مجازی", color = Color(0xFFBBA8D8), fontSize = 13.sp)
                Text(wallet?.balance?.toString() ?: "—", fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("این امتیازها فقط داخل EX1 کاربرد دارند و ارزش نقدی یا امکان برداشت ندارند.", color = Color(0xFF9B91AA), fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("سوابق اخیر", fontWeight = FontWeight.Bold)
        if (wallet?.history.isNullOrEmpty()) {
            Text("تراکنشی برای نمایش وجود ندارد.", color = Color(0xFFAAA2B8), modifier = Modifier.padding(top = 12.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 420.dp)) {
                items(wallet!!.history) { entry ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF191426))) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(if (entry.type == "credit") "افزایش امتیاز" else "کسر امتیاز", fontWeight = FontWeight.Bold)
                            Text("مقدار: ${entry.amount}  ·  موجودی پس از ثبت: ${entry.balanceAfter}", color = Color(0xFFC4BBD2), fontSize = 12.sp)
                            if (entry.reason.isNotBlank()) Text(entry.reason, color = Color(0xFF8E879B), fontSize = 12.sp)
                            Text(entry.createdAt, color = Color(0xFF8E879B), fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomPreviewCard(room: NativeRoom) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF191426))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(room.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    when (room.status) {
                        "open" -> "باز"
                        "full" -> "تکمیل"
                        "completed" -> "پایان‌یافته"
                        else -> room.status
                    },
                    color = if (room.status == "completed") Color(0xFF898393) else Color(0xFFC5A4FF),
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("ورودی: ${room.entryPoints} امتیاز مجازی  ·  ظرفیت: ${room.capacity}", color = Color(0xFFC4BBD2), fontSize = 13.sp)
            Text("جایزه اول: ${room.firstPrize} امتیاز مجازی", color = Color(0xFFBFA0F4), fontSize = 13.sp)
            val meta = listOfNotNull(room.gameMode, room.map).joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, color = Color(0xFF8E879B), fontSize = 12.sp)
        }
    }
}

@Composable
private fun AdminPreviewPanel(
    permissions: List<String>,
    error: String?,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("EX1 LEGEND", fontWeight = FontWeight.Black, fontSize = 26.sp)
        Text("پیش‌نمایش Native پنل مدیریت", color = Color(0xFFBBA8D8))
        Spacer(Modifier.height(16.dp))
        Text("مجوزهای تأییدشده حساب", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (permissions.isEmpty()) Text("برای این حساب مجوز مدیریتی فعالی دریافت نشد.", color = Color(0xFFB6AEC2))
        else permissions.forEach { Text("• $it", color = Color(0xFFD9CDEA), modifier = Modifier.padding(vertical = 4.dp)) }
        if (!error.isNullOrBlank()) Text(error, color = Color(0xFFFFA2A2))
        Spacer(Modifier.weight(1f))
        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("به‌روزرسانی مجوزها") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("خروج از حساب") }
    }
}
