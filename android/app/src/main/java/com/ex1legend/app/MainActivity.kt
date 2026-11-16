package com.ex1legend.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<Ex1Session?>(null) }
    var profile by remember { mutableStateOf<Ex1Profile?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var booting by remember { mutableStateOf(true) }
    var selected by remember { mutableIntStateOf(0) }
    var openedRoom by remember { mutableStateOf<Ex1Room?>(null) }

    LaunchedEffect(Unit) {
        val saved = loadSession(context)
        if (saved != null) {
            try {
                profile = Ex1Api.profile(saved)
                session = saved
            } catch (e: Ex1ApiException) {
                if (e.statusCode == 401) {
                    try {
                        val renewed = Ex1Api.refresh(saved)
                        saveSession(context, renewed)
                        profile = Ex1Api.profile(renewed)
                        session = renewed
                    } catch (_: Exception) { clearSession(context) }
                } else {
                    message = "اتصال به سرور برقرار نشد؛ اینترنت را بررسی کن."
                    session = saved
                }
            } catch (_: Exception) {
                message = "فعلاً دریافت اطلاعات حساب ممکن نیست."
                session = saved
            }
        }
        booting = false
    }

    if (booting) { LoadingScreen("در حال آماده‌سازی EX1..."); return }
    val active = session
    if (active == null) {
        AuthScreen(loading, message) { email, password, displayName, register ->
            scope.launch {
                loading = true
                message = ""
                try {
                    val signed = if (register) Ex1Api.signUp(email, password, displayName)
                        else Ex1Api.signIn(email, password)
                    if (signed == null) {
                        message = "حساب ساخته شد. ایمیل تأیید را بررسی کن و سپس وارد شو."
                    } else {
                        saveSession(context, signed)
                        profile = Ex1Api.profile(signed)
                        session = signed
                    }
                } catch (e: Exception) { message = e.message ?: "ورود یا ثبت‌نام انجام نشد." }
                finally { loading = false }
            }
        }
        return
    }

    Scaffold(containerColor = Night, bottomBar = {
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
    }) { padding ->
        when (selected) {
            0 -> if (openedRoom == null) RoomsScreen(Modifier.padding(padding), active) { openedRoom = it }
                 else RoomDetailsScreen(Modifier.padding(padding), active, openedRoom!!, onBack = { openedRoom = null })
            1 -> WalletScreen(Modifier.padding(padding), active)
            else -> ProfileScreen(
                Modifier.padding(padding),
                active,
                profile,
                onProfileUpdated = { scope.launch { profile = Ex1Api.profile(active) } },
                onLogout = {
                    scope.launch {
                        Ex1Api.signOut(active)
                        clearSession(context)
                        session = null
                        profile = null
                        selected = 0
                    }
                }
            )
        }
    }
}

@Composable
private fun AdminApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<Ex1Session?>(null) }
    var permissions by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var booting by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val saved = loadSession(context)
        if (saved != null) {
            try {
                permissions = Ex1Api.adminPermissions(saved)
                if (permissions.isNotEmpty()) session = saved else clearSession(context)
            } catch (e: Ex1ApiException) {
                if (e.statusCode == 401) {
                    try {
                        val renewed = Ex1Api.refresh(saved)
                        saveSession(context, renewed)
                        permissions = Ex1Api.adminPermissions(renewed)
                        if (permissions.isNotEmpty()) session = renewed else clearSession(context)
                    } catch (_: Exception) { clearSession(context) }
                } else { message = "اتصال به سرور برقرار نشد." }
            } catch (_: Exception) { message = "دریافت مجوزهای مدیریت ممکن نشد." }
        }
        booting = false
    }
    if (booting) { LoadingScreen("در حال بررسی دسترسی..."); return }
    val active = session
    if (active == null) {
        AuthScreen(loading, message) { email, password, _, register ->
            scope.launch {
                loading = true
                message = ""
                try {
                    if (register) {
                        message = "برای پنل مدیریت ابتدا باید حساب بازیکن بسازی و مالک به آن حساب مجوز بدهد."
                    } else {
                        val signed = Ex1Api.signIn(email, password)
                        val allowed = Ex1Api.adminPermissions(signed)
                        if (allowed.isEmpty()) {
                            Ex1Api.signOut(signed)
                            message = "این حساب مجوز ورود به پنل مدیریت را ندارد."
                        } else {
                            saveSession(context, signed)
                            session = signed
                            permissions = allowed
                        }
                    }
                } catch (e: Exception) { message = e.message ?: "ورود انجام نشد." }
                finally { loading = false }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().background(Night).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Heading("پنل مدیریت EX1", "دسترسی‌ها از سرور بررسی شده‌اند")
        InfoCard("وضعیت دسترسی", "این حساب ${permissions.size} مجوز فعال دارد. مجوزها از سرور خوانده شده‌اند.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(permissions) { permission -> InfoCard(permission, "مجوز تأییدشده توسط سرور") }
        }
        OutlinedButton(onClick = {
            scope.launch {
                Ex1Api.signOut(active)
                clearSession(context)
                session = null
                permissions = emptyList()
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("خروج از پنل مدیریت") }
    }
}

@Composable
private fun AuthScreen(
    loading: Boolean,
    message: String,
    onSubmit: (String, String, String, Boolean) -> Unit
) {
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
                    Button(
                        onClick = { onSubmit(email.trim(), password, displayName.trim(), register) },
                        enabled = !loading && email.contains("@") && password.length >= 8 && (!register || displayName.trim().length >= 2),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text(if (loading) "در حال ارتباط..." else if (register) "ثبت‌نام" else "ورود") }
                    Text(
                        if (register) "حساب داری؟ ورود" else "حساب نداری؟ ثبت‌نام",
                        Modifier.fillMaxWidth().clickable(enabled = !loading) { register = !register }.padding(8.dp),
                        color = Violet,
                        textAlign = TextAlign.Center
                    )
                    if (message.isNotBlank()) Text(message, color = Color(0xFFFFB6C1), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("استارها امتیاز داخلی و غیرنقدی EX1 هستند.", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun RoomsScreen(modifier: Modifier, session: Ex1Session, onRoomClick: (Ex1Room) -> Unit) {
    var rooms by remember { mutableStateOf<List<Ex1Room>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(session.userId) {
        loading = true
        try { rooms = Ex1Api.rooms(session); error = "" }
        catch (e: Exception) { error = e.message ?: "دریافت روم‌ها ممکن نشد." }
        finally { loading = false }
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
    ) {
        item { Heading("روم‌های مسابقه", "فهرست روم‌های قابل مشاهده در EX1") }
        if (loading) item { InfoCard("در حال دریافت روم‌ها...", "اطلاعات مستقیم از Supabase دریافت می‌شود.") }
        if (error.isNotBlank()) item { InfoCard("خطا در دریافت اطلاعات", error) }
        if (!loading && error.isBlank() && rooms.isEmpty()) item { InfoCard("روم فعالی پیدا نشد", "وقتی روم قابل مشاهده منتشر شود، اینجا نمایش داده می‌شود.") }
        items(rooms, key = { it.id }) { RoomCard(it) { onRoomClick(it) } }
    }
}

@Composable
private fun RoomCard(room: Ex1Room, onClick: () -> Unit) {
    val phase = roomPhase(room)
    Card(colors = CardDefaults.cardColors(containerColor = if (phase == "completed") Color(0xFF22212A) else Panel),
        shape = RoundedCornerShape(18.dp), modifier = Modifier.clickable(onClick = onClick)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(room.title, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Color.White)
                Text(roomPhaseLabel(room), color = if (phase == "completed") Muted else Violet, fontSize = 11.sp)
            }
            Text(listOfNotNull(room.gameMode, room.map).joinToString(" · ").ifBlank { "Call of Duty Mobile" }, color = Muted, fontSize = 12.sp)
            Text("تیم ${room.teamSize} نفره · ظرفیت ${room.maxTeams} تیم", color = Muted, fontSize = 12.sp)
            Text("هزینه ورود: ${room.entryPoints} استار", color = Color(0xFFC8A8FF), fontSize = 12.sp)
            if (room.firstPrize + room.secondPrize + room.thirdPrize > 0) {
                Text("امتیازهای نمایشی: اول ${room.firstPrize} · دوم ${room.secondPrize} · سوم ${room.thirdPrize}", color = Muted, fontSize = 11.sp)
            }
            room.startsAt?.let { Text("شروع: ${formatDate(it)}", color = Muted, fontSize = 11.sp) }
            room.endsAt?.let { Text("پایان: ${formatDate(it)}", color = Muted, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun RoomDetailsScreen(
    modifier: Modifier,
    session: Ex1Session,
    room: Ex1Room,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var slots by remember { mutableStateOf<List<Ex1RoomSlot>>(emptyList()) }
    var soloStatus by remember { mutableStateOf<Ex1SoloStatus?>(null) }
    var loading by remember { mutableStateOf(true) }
    var registering by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    suspend fun refresh() {
        loading = true
        try {
            slots = Ex1Api.roomSlots(session, room.id)
            soloStatus = Ex1Api.mySoloStatus(session, room.id)
            error = ""
        } catch (e: Exception) {
            error = e.message ?: "دریافت جزئیات روم ممکن نشد."
        } finally { loading = false }
    }

    LaunchedEffect(room.id) { refresh() }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 28.dp)
    ) {
        item {
            Text("‹ بازگشت به روم‌ها", Modifier.clickable(onClick = onBack).padding(vertical = 8.dp), color = Violet)
        }
        item { Heading(room.title, "جزئیات و وضعیت ثبت‌نام") }
        item { InfoCard("وضعیت روم", roomPhaseLabel(room)) }
        item { InfoCard("اطلاعات مسابقه", "حالت: ${room.gameMode ?: "نامشخص"} · نقشه: ${room.map ?: "نامشخص"}\nتیم ${room.teamSize} نفره · ظرفیت ${room.maxTeams} تیم\nشروع: ${room.startsAt?.let { formatDate(it) } ?: "اعلام نشده"}\nپایان: ${room.endsAt?.let { formatDate(it) } ?: "اعلام نشده"}") }
        item { InfoCard("هزینه ورود", "${room.entryPoints} استار داخلی و غیرنقدی") }
        item { Text("جایگاه‌ها و بازیکنان", color = Color.White, fontWeight = FontWeight.Bold) }
        if (loading) item { InfoCard("در حال دریافت...", "وضعیت جایگاه‌ها از سرور خوانده می‌شود.") }
        if (error.isNotBlank()) item { InfoCard("خطا", error) }
        if (!loading && slots.isEmpty() && error.isBlank()) item { InfoCard("هنوز جایگاهی ثبت نشده", "با ثبت‌نام بازیکنان، وضعیت جایگاه‌ها اینجا نمایش داده می‌شود.") }
        items(slots, key = { it.number }) { slot ->
            InfoCard(
                "جایگاه ${slot.number} · ${slot.teamName}",
                "${if (slot.status == "waiting") "در حال تشکیل تیم" else "ثبت‌نام شده"}\n${slot.players.joinToString("، ").ifBlank { "اطلاعات بازیکنان موجود نیست" }}"
            )
        }
        soloStatus?.takeIf { it.registered }?.let { status ->
            item {
                InfoCard(
                    "ثبت‌نام انفرادی شما",
                    when (status.status) {
                        "waiting" -> "در صف جایگاه ${status.slotNumber} هستی. ${status.waiting ?: 0} نفر از ${room.teamSize} نفر ثبت‌نام کرده‌اند؛ ${status.needed ?: 0} نفر دیگر نیاز است."
                        "matched" -> "تیمت در جایگاه ${status.slotNumber} تشکیل شده است."
                        else -> "وضعیت: ${status.status ?: "نامشخص"}"
                    }
                )
            }
        }
        item {
            Text("ثبت‌نام در مسابقه", color = Color.White, fontWeight = FontWeight.Bold)
        }
        item {
            Button(
                onClick = {
                    scope.launch {
                        registering = true
                        message = ""
                        try {
                            val result = Ex1Api.registerSolo(session, room.id)
                            message = when (result.optString("status")) {
                                "matched" -> "ثبت‌نام انجام شد و تیم انفرادی تشکیل شد."
                                "waiting" -> "ثبت‌نام انجام شد؛ در انتظار تکمیل تیم هستی."
                                else -> "درخواست ثبت‌نام ثبت شد."
                            }
                            refresh()
                        } catch (e: Exception) { message = e.message ?: "ثبت‌نام انفرادی انجام نشد." }
                        finally { registering = false }
                    }
                },
                enabled = !registering && room.status == "open" && roomPhase(room) !in listOf("pending", "completed"),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (registering) "در حال ثبت‌نام..." else "ثبت‌نام انفرادی و تشکیل خودکار تیم") }
        }
        item {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        registering = true
                        message = ""
                        try {
                            val result = Ex1Api.registerTeam(session, room.id)
                            message = "درخواست ثبت‌نام تیمی برای جایگاه ${result.optInt("slot_number")} ارسال شد."
                            refresh()
                        } catch (e: Exception) { message = e.message ?: "ثبت‌نام تیمی انجام نشد." }
                        finally { registering = false }
                    }
                },
                enabled = !registering && room.status == "open" && roomPhase(room) !in listOf("pending", "completed"),
                modifier = Modifier.fillMaxWidth()
            ) { Text("ثبت‌نام با تیم کامل (ویژه کاپیتان)") }
        }
        if (message.isNotBlank()) item { InfoCard("نتیجه درخواست", message) }
        item { Text("استارها امتیاز داخلی‌اند و ارزش نقدی یا جایزه واقعی ندارند.", color = Muted, fontSize = 11.sp) }
    }
}

@Composable
private fun WalletScreen(modifier: Modifier, session: Ex1Session) {
    var wallet by remember { mutableStateOf<Ex1Wallet?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(session.userId) {
        loading = true
        try { wallet = Ex1Api.wallet(session); error = "" }
        catch (e: Exception) { error = e.message ?: "دریافت کیف استار ممکن نشد." }
        finally { loading = false }
    }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Heading("کیف استار", "امتیازهای داخلی و غیرنقدی EX1") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp)) {
                    Text("موجودی استار", color = Muted)
                    Text(if (loading) "..." else wallet?.balance?.toString() ?: "—", fontSize = 36.sp, fontWeight = FontWeight.Black)
                    Text("استارها قابل خرید یا تبدیل به پول نیستند.", color = Muted, fontSize = 11.sp)
                }
            }
        }
        if (error.isNotBlank()) item { InfoCard("خطا", error) }
        item { Text("تاریخچه", color = Color.White, fontWeight = FontWeight.Bold) }
        if (!loading && wallet?.history.isNullOrEmpty()) item { InfoCard("تاریخچه خالی است", "هنوز رویدادی در دفترکل استار ثبت نشده است.") }
        items(wallet?.history ?: emptyList()) { entry ->
            InfoCard(
                if (entry.type == "credit") "دریافت استار" else "مصرف استار",
                "${entry.reason} · ${if (entry.type == "credit") "+" else "−"}${entry.amount} استار · مانده: ${entry.balanceAfter}"
            )
        }
    }
}

@Composable
private fun ProfileScreen(
    modifier: Modifier,
    session: Ex1Session,
    profile: Ex1Profile?,
    onProfileUpdated: () -> Unit,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var displayName by remember { mutableStateOf(profile?.displayName.orEmpty()) }
    var username by remember { mutableStateOf(profile?.username.orEmpty()) }
    var gameName by remember { mutableStateOf(profile?.gameName.orEmpty()) }
    var gameUid by remember { mutableStateOf(profile?.gameUid.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var confirmLogout by remember { mutableStateOf(false) }

    LaunchedEffect(profile?.id) {
        displayName = profile?.displayName.orEmpty()
        username = profile?.username.orEmpty()
        gameName = profile?.gameName.orEmpty()
        gameUid = profile?.gameUid.orEmpty()
    }

    Column(modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Heading("پروفایل بازیکن", "اطلاعات حساب EX1")
        InfoCard("ایمیل حساب", session.email)
        Field(displayName, { displayName = it }, "نام نمایشی")
        Field(username, { username = it }, "نام کاربری")
        Field(gameName, { gameName = it }, "نام داخل بازی")
        Field(gameUid, { gameUid = it }, "UID بازی")
        Button(
            onClick = {
                scope.launch {
                    saving = true
                    message = ""
                    try {
                        val result = Ex1Api.updateProfile(session, displayName.trim(), username.trim(), gameUid.trim(), gameName.trim())
                        if (result.optBoolean("success")) {
                            message = "پروفایل با موفقیت ذخیره شد."
                            onProfileUpdated()
                        } else {
                            message = result.optString("message").ifBlank { "اطلاعات برای بررسی ارسال شد و هنوز ذخیره نشده است." }
                        }
                    } catch (e: Exception) {
                        message = e.message ?: "ذخیره پروفایل انجام نشد."
                    } finally { saving = false }
                }
            },
            enabled = !saving && displayName.trim().length >= 2 && !gameUid.contains("@"),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (saving) "در حال ذخیره..." else "ذخیره تغییرات")
        }
        if (message.isNotBlank()) Text(message, color = Violet, fontSize = 12.sp)
        OutlinedButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth()) {
            Text("خروج از حساب")
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("خروج از حساب") },
            text = { Text("آیا می‌خواهی از حساب کاربری خود خارج شوی؟") },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("خروج") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("انصراف") }
            }
        )
    }
}

@Composable
private fun Field(value: String, change: (String) -> Unit, label: String,
    keyboard: KeyboardType = KeyboardType.Text, password: Boolean = false) {
    OutlinedTextField(
        value, change, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
    )
}

@Composable
private fun LoadingScreen(text: String) {
    Box(Modifier.fillMaxSize().background(Night), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Violet)
            Spacer(Modifier.height(16.dp))
            Text(text, color = Muted)
        }
    }
}

@Composable
private fun Heading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Color.White)
            Text(body, color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
        }
    }
}

private fun roomPhase(room: Ex1Room): String {
    val end = room.endsAt?.let { parseIso(it) } ?: return if (room.status == "completed") "completed" else "scheduled"
    val now = System.currentTimeMillis()
    return when {
        now < end -> "scheduled"
        now < end + 3_600_000L -> "running"
        now < end + 7_200_000L -> "pending"
        else -> "completed"
    }
}

private fun roomPhaseLabel(room: Ex1Room): String = when (roomPhase(room)) {
    "running" -> "در حال برگزاری"
    "pending" -> "در انتظار ثبت نتایج"
    "completed" -> "تمام شده"
    else -> when (room.status) {
        "open" -> "ثبت‌نام فعال"
        "full" -> "ظرفیت تکمیل"
        else -> "برنامه‌ریزی‌شده"
    }
}

private fun parseIso(value: String): Long? = try {
    val normalized = value.replace("Z", "+0000").replace(Regex("([+-][0-9]{2}):([0-9]{2})$"), "$1$2")
    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", java.util.Locale.US).parse(normalized)?.time
        ?: java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.US).parse(normalized)?.time
} catch (_: Exception) { null }

private fun formatDate(value: String): String = value.replace("T", " ").replace("Z", "").take(16)
