package com.ex1legend.app

import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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

private fun parseIsoDate(value: String?): Date? {
    if (value.isNullOrBlank()) return null
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX"
    )
    for (pattern in patterns) {
        val parser = SimpleDateFormat(pattern, Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val parsed = runCatching { parser.parse(value) }.getOrNull()
        if (parsed != null) return parsed
    }
    return null
}

private fun isRoomFinished(room: NativeRoom): Boolean {
    if (room.status == "completed") return true
    val end = parseIsoDate(room.endsAt) ?: return false
    return System.currentTimeMillis() >= end.time + 2 * 60 * 60 * 1000L
}

private fun isRoomRegistrationOpen(room: NativeRoom): Boolean {
    if (room.status != "open") return false
    val startValue = room.startsAt ?: return true
    val start = parseIsoDate(startValue) ?: return false
    return System.currentTimeMillis() < start.time
}

private fun currentIsoTimestamp(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date())

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
    var explorePlayers by remember { mutableStateOf<List<NativePublicProfile>>(emptyList()) }
    var exploreQuery by remember { mutableStateOf("") }
    var selectedPublicProfile by remember { mutableStateOf<NativePublicProfile?>(null) }
    var publicProfileLoading by remember { mutableStateOf(false) }
    var publicProfileError by remember { mutableStateOf<String?>(null) }
    var wallet by remember { mutableStateOf<NativeWallet?>(null) }
    var permissions by remember { mutableStateOf<List<String>>(emptyList()) }
    var adminSnapshot by remember { mutableStateOf(NativeAdminSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())) }
    var team by remember { mutableStateOf<NativeTeam?>(null) }
    var notifications by remember { mutableStateOf<List<NativeNotification>>(emptyList()) }
    var supportConversationId by remember { mutableStateOf<String?>(null) }
    var supportFresh by remember { mutableStateOf(false) }
    var supportMessages by remember { mutableStateOf<List<NativeSupportMessage>>(emptyList()) }
    var supportHint by remember { mutableStateOf("برای شروع، پیام خودت را بنویس.") }
    var supportError by remember { mutableStateOf<String?>(null) }
    var supportHandoff by remember { mutableStateOf(false) }
    var supportBusy by remember { mutableStateOf(false) }
    var supportSendRevision by remember { mutableStateOf(0) }
    var teamInvites by remember { mutableStateOf<List<NativeTeamInvite>>(emptyList()) }
    var sentTeamInvites by remember { mutableStateOf<List<NativeSentTeamInvite>>(emptyList()) }
    var teamMessage by remember { mutableStateOf<String?>(null) }
    var teamError by remember { mutableStateOf<String?>(null) }
    var ownerAccessDenied by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(session != null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf("all") }
    var price by remember { mutableStateOf("all") }
    var activeTab by remember { mutableStateOf("rooms") }
    var selectedRoom by remember { mutableStateOf<NativeRoom?>(null) }
    var roomSlots by remember { mutableStateOf<List<NativeRoomSlot>>(emptyList()) }
    var roomResults by remember { mutableStateOf<List<NativePlayerResult>>(emptyList()) }
    var soloStatus by remember { mutableStateOf<NativeSoloStatus?>(null) }
    var roomAccess by remember { mutableStateOf<NativeRoomAccess?>(null) }
    var roomDetailLoading by remember { mutableStateOf(false) }
    var roomActionBusy by remember { mutableStateOf(false) }
    var roomDetailError by remember { mutableStateOf<String?>(null) }
    var roomActionMessage by remember { mutableStateOf<String?>(null) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDeleteAccount by remember { mutableStateOf(false) }
    var showDeleteForm by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    var deletePhrase by remember { mutableStateOf("") }

    LaunchedEffect(session?.userId) {
        val current = session ?: return@LaunchedEffect
        loading = true
        error = null
        try {
            val renewed = NativeEx1Api.refresh(current)
            if (NativeEx1Api.isCurrentUserBanned(renewed)) {
                runCatching { NativeEx1Api.signOut(renewed) }
                NativeEx1Api.clearSession(context)
                session = null
                rooms = emptyList(); profile = null; explorePlayers = emptyList(); selectedPublicProfile = null; publicProfileLoading = false; publicProfileError = null; wallet = null; permissions = emptyList(); selectedRoom = null; roomResults = emptyList(); roomSlots = emptyList(); soloStatus = null; roomAccess = null; team = null; teamInvites = emptyList(); sentTeamInvites = emptyList(); notifications = emptyList(); supportMessages = emptyList(); supportConversationId = null; supportFresh = false; adminSnapshot = NativeAdminSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()); roomResults = emptyList()
                error = "دسترسی این حساب به EX1 مسدود شده است. برای پیگیری با پشتیبانی تماس بگیر."
                return@LaunchedEffect
            }
            NativeEx1Api.saveSession(context, renewed)
            session = renewed
            if (isAdmin) {
                ownerAccessDenied = !NativeEx1Api.isCurrentUserOwner(renewed)
                permissions = if (ownerAccessDenied) emptyList() else NativeEx1Api.adminPermissions(renewed)
                adminSnapshot = if (ownerAccessDenied) NativeAdminSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
                    else NativeEx1Api.adminSnapshot(renewed, permissions)
            } else {
                rooms = NativeEx1Api.rooms(renewed)
            }
        } catch (e: NativeApiException) {
            if (e.status == 400 || e.status == 401 || e.status == 403) {
                NativeEx1Api.clearSession(context)
                session = null
                rooms = emptyList(); profile = null; wallet = null; permissions = emptyList(); selectedRoom = null; roomResults = emptyList(); roomSlots = emptyList(); soloStatus = null; roomAccess = null; team = null; teamInvites = emptyList(); sentTeamInvites = emptyList(); notifications = emptyList(); supportMessages = emptyList(); supportConversationId = null; supportFresh = false; adminSnapshot = NativeAdminSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
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

    val openRoom: (NativeRoom) -> Unit = { room ->
        selectedRoom = room
        roomSlots = emptyList()
        roomResults = emptyList()
        soloStatus = null
        roomAccess = null
        roomDetailError = null
        roomActionMessage = null
        roomDetailLoading = true
        scope.launch {
            try {
                val current = session ?: throw IllegalStateException("ابتدا وارد حساب شو.")
                val loadedSlots = NativeEx1Api.roomSlots(current, room.id)
                val loadedSoloStatus = NativeEx1Api.soloStatus(current, room.id)
                val loadedAccess = NativeEx1Api.roomAccess(current, room.id)
                val loadedResults = if (isRoomFinished(room)) NativeEx1Api.roomResults(current, room.id) else emptyList()
                if (selectedRoom?.id == room.id) {
                    roomSlots = loadedSlots
                    soloStatus = loadedSoloStatus
                    roomAccess = loadedAccess
                    roomResults = loadedResults
                }
            } catch (e: Exception) {
                if (selectedRoom?.id == room.id) {
                    roomDetailError = e.message ?: "دریافت اطلاعات روم ناموفق بود."
                }
            } finally {
                if (selectedRoom?.id == room.id) roomDetailLoading = false
            }
        }
    }

    val registerRoom: (NativeRoom, Int, Boolean) -> Unit = { room, slotNumber, solo ->
        if (!isRoomRegistrationOpen(room)) {
            roomDetailError = "ثبت‌نام این روم در وضعیت فعلی باز نیست."
        } else {
            scope.launch {
                roomActionBusy = true
                roomDetailError = null
                roomActionMessage = null
                try {
                    val current = session ?: throw IllegalStateException("ابتدا وارد حساب شو.")
                    val currentProfile = profile ?: NativeEx1Api.profile(current).also { profile = it }
                    if (currentProfile?.gameUid.isNullOrBlank() ||
                        currentProfile?.gameName.isNullOrBlank() ||
                        currentProfile?.gameUid?.contains("@") == true
                    ) {
                        throw IllegalStateException("برای ثبت‌نام، UID و نام داخل بازی کالاف را در پروفایل تکمیل کن.")
                    }
                    val result = if (solo) {
                        NativeEx1Api.registerSolo(current, room.id, slotNumber)
                    } else {
                        NativeEx1Api.registerForRoom(current, room.id, slotNumber)
                    }
                    if (selectedRoom?.id == room.id) {
                        roomActionMessage = if (solo && result.optString("status") == "matched") {
                            "تیمت به‌صورت خودکار تشکیل شد؛ جایگاه ${result.optInt("slot_number")}."
                        } else if (solo) {
                            "ثبت‌نام انفرادی در صف ثبت شد؛ ${result.optInt("players_waiting")} نفر از ${result.optInt("team_size")} نفر."
                        } else {
                            "ثبت‌نام با موفقیت ثبت شد؛ جایگاه ${result.optInt("slot_number")}."
                        }
                    }
                    wallet = NativeEx1Api.wallet(current)
                    if (selectedRoom?.id == room.id) {
                        roomSlots = NativeEx1Api.roomSlots(current, room.id)
                        soloStatus = NativeEx1Api.soloStatus(current, room.id)
                        roomAccess = NativeEx1Api.roomAccess(current, room.id)
                    }
                } catch (e: Exception) {
                    if (selectedRoom?.id == room.id) {
                        roomDetailError = e.message ?: "ثبت‌نام انجام نشد."
                    }
                } finally {
                    roomActionBusy = false
                }
            }
        }
    }

    suspend fun refreshTeam(current: NativeSession) {
        team = NativeEx1Api.myTeam(current)
        teamInvites = NativeEx1Api.incomingTeamInvites(current)
        sentTeamInvites = if (team?.isCaptain == true) NativeEx1Api.sentTeamInvites(current, team!!.id) else emptyList()
    }

    val saveProfile: (String, String, String, String, String) -> Unit = { name, username, uid, gameName, bio ->
        scope.launch {
            val current = session ?: return@launch
            loading = true
            try {
                val result = NativeEx1Api.updateProfile(current, name, username, uid, gameName, bio)
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
                                    } else if (NativeEx1Api.isCurrentUserBanned(created)) {
                                        runCatching { NativeEx1Api.signOut(created) }
                                        error = "دسترسی این حساب به EX1 مسدود شده است."
                                    } else {
                                        NativeEx1Api.saveSession(context, created)
                                        session = created
                                    }
                                } else {
                                    val loggedIn = NativeEx1Api.signIn(email, password)
                                    if (NativeEx1Api.isCurrentUserBanned(loggedIn)) {
                                        runCatching { NativeEx1Api.signOut(loggedIn) }
                                        throw IllegalStateException("دسترسی این حساب به EX1 مسدود شده است.")
                                    }
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
                snapshot = adminSnapshot,
                ownerAccessDenied = ownerAccessDenied,
                error = error,
                onRefresh = {
                    scope.launch {
                        loading = true
                        try {
                            val current = session ?: return@launch
                            ownerAccessDenied = !NativeEx1Api.isCurrentUserOwner(current)
                            permissions = if (ownerAccessDenied) emptyList() else NativeEx1Api.adminPermissions(current)
                            adminSnapshot = if (ownerAccessDenied) NativeAdminSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
                                else NativeEx1Api.adminSnapshot(current, permissions)
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
                explorePlayers = explorePlayers,
                exploreQuery = exploreQuery,
                onExploreQuery = { exploreQuery = it },
                onSearchExplore = {
                    scope.launch {
                        val current = session ?: return@launch
                        loading = true
                        try { explorePlayers = NativeEx1Api.explorePlayers(current, exploreQuery); error = null }
                        catch (e: Exception) { error = e.message ?: "جست‌وجوی بازیکنان انجام نشد." }
                        finally { loading = false }
                    }
                },
                onOpenPublicProfile = { playerId ->
                    scope.launch {
                        val current = session ?: return@launch
                        selectedPublicProfile = null
                        publicProfileError = null
                        publicProfileLoading = true
                        try {
                            selectedPublicProfile = NativeEx1Api.publicProfile(current, playerId)
                            if (selectedPublicProfile == null) publicProfileError = "پروفایل این بازیکن پیدا نشد یا در دسترس نیست."
                        } catch (e: Exception) {
                            publicProfileError = e.message ?: "دریافت پروفایل بازیکن انجام نشد."
                        } finally { publicProfileLoading = false }
                    }
                },
                wallet = wallet,
                activeTab = activeTab,
                onTab = { tab ->
                    activeTab = tab
                    val current = session
                    if (current != null && tab != "rooms") {
                        scope.launch {
                            loading = true
                            try {
                                when (tab) {
                                    "profile" -> profile = NativeEx1Api.profile(current)
                                    "explore" -> explorePlayers = NativeEx1Api.explorePlayers(current, exploreQuery)
                                    "wallet" -> wallet = NativeEx1Api.wallet(current)
                                    "team" -> refreshTeam(current)
                                    "notifications" -> notifications = NativeEx1Api.notifications(current)
                                    "support" -> {
                                        if (!supportFresh) {
                                            val latest = NativeEx1Api.latestSupportConversation(current)
                                            supportConversationId = latest?.id
                                            supportMessages = if (latest == null) emptyList() else NativeEx1Api.supportMessages(current, latest.id)
                                            supportHint = if (latest == null) "گفت‌وگوی تازه آماده است؛ پیام اول را بنویس." else "گفت‌وگوی پشتیبانی"
                                        }
                                    }
                                }
                                error = null
                            } catch (e: Exception) {
                                error = e.message ?: "دریافت اطلاعات انجام نشد."
                            } finally {
                                loading = false
                            }
                        }
                    }
                },
                onOpenRoom = openRoom,
                notifications = notifications,
                supportMessages = supportMessages,
                supportHint = supportHint,
                supportError = supportError,
                supportHandoff = supportHandoff,
                supportBusy = supportBusy,
                supportSendRevision = supportSendRevision,
                onNewSupport = {
                    supportFresh = true
                    supportConversationId = null
                    supportMessages = emptyList()
                    supportHandoff = false
                    supportError = null
                    supportHint = "گفت‌وگوی تازه آماده است؛ پیام اول را بنویس."
                },
                onSendSupport = { message ->
                    scope.launch {
                        supportBusy = true
                        supportError = null
                        supportHint = "در حال دریافت پاسخ..."
                        try {
                            val current = session ?: throw IllegalStateException("ابتدا وارد حساب شو.")
                            val result = NativeEx1Api.sendSupportMessage(current, message, supportConversationId)
                            if (!result.optBoolean("success")) throw IllegalStateException(result.optString("error", "پاسخی دریافت نشد."))
                            supportConversationId = result.optString("conversation_id").takeIf { it.isNotBlank() && it != "null" }
                            supportFresh = false
                            supportHandoff = result.optBoolean("handoff")
                            supportMessages = if (supportConversationId == null) supportMessages else NativeEx1Api.supportMessages(current, supportConversationId!!)
                            supportHint = if (supportHandoff) "این گفت‌وگو در صف پشتیبان انسانی است." else "پاسخ پشتیبانی دریافت شد."
                            supportSendRevision++
                        } catch (e: Exception) {
                            supportError = e.message ?: "ارسال پیام انجام نشد."
                            supportHint = "ارسال پیام ناموفق بود؛ دوباره تلاش کن."
                        } finally {
                            supportBusy = false
                        }
                    }
                },
                onMarkNotificationRead = { notificationId ->
                    scope.launch {
                        val current = session ?: return@launch
                        try {
                            NativeEx1Api.markNotificationRead(current, notificationId, currentIsoTimestamp())
                            notifications = NativeEx1Api.notifications(current)
                        } catch (e: Exception) {
                            error = e.message ?: "ثبت وضعیت اعلان ناموفق بود."
                        }
                    }
                },
                onDeleteNotification = { notificationId ->
                    scope.launch {
                        val current = session ?: return@launch
                        try {
                            NativeEx1Api.deleteNotification(current, notificationId)
                            notifications = NativeEx1Api.notifications(current)
                        } catch (e: Exception) {
                            error = e.message ?: "حذف اعلان ناموفق بود."
                        }
                    }
                },
                team = team,
                teamBusy = busy,
                teamInvites = teamInvites,
                sentTeamInvites = sentTeamInvites,
                teamMessage = teamMessage,
                teamError = teamError,
                onCreateTeam = { name, tag, description ->
                    scope.launch {
                        busy = true; teamError = null; teamMessage = null
                        try {
                            val current = session ?: throw IllegalStateException("ابتدا وارد حساب شو.")
                            val result = NativeEx1Api.createTeam(current, name, tag, description)
                            if (result.optBoolean("pending_review")) teamMessage = result.optString("message", "درخواست ساخت تیم برای بررسی ارسال شد.")
                            else if (!result.optBoolean("success")) throw IllegalStateException(result.optString("error", "ساخت تیم انجام نشد."))
                            else { refreshTeam(current); teamMessage = "تیم ساخته شد." }
                        } catch (e: Exception) { teamError = e.message ?: "ساخت تیم انجام نشد." }
                        finally { busy = false }
                    }
                },
                onUpdateTeam = { name, tag, description ->
                    scope.launch {
                        busy = true; teamError = null; teamMessage = null
                        try {
                            val current = session ?: throw IllegalStateException("ابتدا وارد حساب شو.")
                            val currentTeam = team ?: throw IllegalStateException("تیمی پیدا نشد.")
                            val result = NativeEx1Api.updateTeam(current, currentTeam.id, name, tag, description)
                            if (result.optBoolean("pending_review")) teamMessage = result.optString("message", "تغییرات برای بررسی مالک ارسال شد.")
                            else if (!result.optBoolean("success")) throw IllegalStateException(result.optString("error", "ویرایش تیم انجام نشد."))
                            else { refreshTeam(current); teamMessage = "اطلاعات تیم به‌روزرسانی شد." }
                        } catch (e: Exception) { teamError = e.message ?: "ویرایش تیم انجام نشد." }
                        finally { busy = false }
                    }
                },
                onInvite = { username ->
                    scope.launch {
                        busy = true; teamError = null; teamMessage = null
                        try {
                            val current = session ?: throw IllegalStateException("ابتدا وارد حساب شو.")
                            NativeEx1Api.createTeamInvite(current, username)
                            refreshTeam(current); teamMessage = "دعوت برای بازیکن ارسال شد."
                        } catch (e: Exception) { teamError = e.message ?: "ارسال دعوت انجام نشد." }
                        finally { busy = false }
                    }
                },
                onRespondInvite = { inviteId, accept ->
                    scope.launch {
                        busy = true; teamError = null; teamMessage = null
                        try {
                            val current = session ?: throw IllegalStateException("ابتدا وارد حساب شو.")
                            NativeEx1Api.respondTeamInvite(current, inviteId, accept)
                            refreshTeam(current)
                            teamMessage = if (accept) "به تیم پیوستی." else "دعوت رد شد."
                        } catch (e: Exception) { teamError = e.message ?: "پاسخ به دعوت انجام نشد." }
                        finally { busy = false }
                    }
                },
                onCancelInvite = { inviteId ->
                    scope.launch {
                        busy = true; teamError = null; teamMessage = null
                        try {
                            val current = session ?: throw IllegalStateException("ابتدا وارد حساب شو.")
                            NativeEx1Api.cancelTeamInvite(current, inviteId)
                            refreshTeam(current); teamMessage = "دعوت لغو شد."
                        } catch (e: Exception) { teamError = e.message ?: "لغو دعوت انجام نشد." }
                        finally { busy = false }
                    }
                },
                onTeamAction = { action, userId ->
                    scope.launch {
                        busy = true; teamError = null; teamMessage = null
                        try {
                            val current = session ?: throw IllegalStateException("ابتدا وارد حساب شو.")
                            when (action) {
                                "leave" -> NativeEx1Api.leaveTeam(current)
                                "delete" -> NativeEx1Api.deleteTeam(current)
                                "remove" -> NativeEx1Api.removeTeamMember(current, userId ?: throw IllegalStateException("شناسه عضو مشخص نیست."))
                            }
                            refreshTeam(current)
                            teamMessage = when (action) { "leave" -> "از تیم خارج شدی."; "delete" -> "تیم حذف شد."; else -> "عضو از تیم خارج شد." }
                        } catch (e: Exception) { teamError = e.message ?: "عملیات تیم انجام نشد." }
                        finally { busy = false }
                    }
                },
                onDeleteAccount = { confirmDeleteAccount = true },
                onSaveProfile = saveProfile,
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
                                "explore" -> explorePlayers = NativeEx1Api.explorePlayers(current, exploreQuery)
                                "wallet" -> wallet = NativeEx1Api.wallet(current)
                                "team" -> refreshTeam(current)
                                "notifications" -> notifications = NativeEx1Api.notifications(current)
                                "support" -> {
                                    val activeConversation = supportConversationId
                                    supportMessages = if (supportFresh || activeConversation == null) emptyList() else NativeEx1Api.supportMessages(current, activeConversation)
                                }
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

        if (publicProfileLoading || selectedPublicProfile != null || publicProfileError != null) {
            PublicProfileDialog(
                profile = selectedPublicProfile,
                loading = publicProfileLoading,
                error = publicProfileError,
                onDismiss = { if (!publicProfileLoading) { selectedPublicProfile = null; publicProfileError = null } }
            )
        }

        if (selectedRoom != null) {
            RoomDetailDialog(
                room = selectedRoom!!,
                slots = roomSlots,
                results = roomResults,
                soloStatus = soloStatus,
                access = roomAccess,
                loading = roomDetailLoading,
                busy = roomActionBusy,
                error = roomDetailError,
                message = roomActionMessage,
                onDismiss = { selectedRoom = null },
                onRefresh = {
                    scope.launch {
                        val current = session ?: return@launch
                        val room = selectedRoom ?: return@launch
                        roomDetailLoading = true
                        try {
                            val loadedSlots = NativeEx1Api.roomSlots(current, room.id)
                            val loadedSoloStatus = NativeEx1Api.soloStatus(current, room.id)
                            val loadedAccess = NativeEx1Api.roomAccess(current, room.id)
                            val loadedResults = if (isRoomFinished(room)) NativeEx1Api.roomResults(current, room.id) else emptyList()
                            if (selectedRoom?.id == room.id) {
                                roomSlots = loadedSlots
                                soloStatus = loadedSoloStatus
                                roomAccess = loadedAccess
                                roomResults = loadedResults
                                roomDetailError = null
                            }
                        } catch (e: Exception) {
                            if (selectedRoom?.id == room.id) roomDetailError = e.message ?: "به‌روزرسانی وضعیت روم ناموفق بود."
                        } finally {
                            if (selectedRoom?.id == room.id) roomDetailLoading = false
                        }
                    }
                },
                onRegister = { slot, solo ->
                    val room = selectedRoom
                    if (room != null) registerRoom(room, slot, solo)
                }
            )
        }

        if (confirmDeleteAccount) {
            AlertDialog(
                onDismissRequest = { confirmDeleteAccount = false },
                title = { Text("حذف دائمی حساب") },
                text = { Text("این عملیات دائمی است و قابل بازگشت نیست. برای ادامه باید هویتت را تأیید کنی.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDeleteAccount = false
                        deletePassword = ""
                        deletePhrase = ""
                        showDeleteForm = true
                    }) { Text("ادامه") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteAccount = false }) { Text("انصراف") }
                }
            )
        }
        if (showDeleteForm) {
            AlertDialog(
                onDismissRequest = { if (!busy) showDeleteForm = false },
                title = { Text("تأیید نهایی حذف حساب") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("رمز فعلی حساب را وارد کن و عبارت «حذف حساب» را دقیقاً بنویس.")
                        OutlinedTextField(
                            value = deletePassword,
                            onValueChange = { deletePassword = it },
                            label = { Text("رمز عبور فعلی") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = deletePhrase,
                            onValueChange = { deletePhrase = it },
                            label = { Text("عبارت تأیید") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !busy && deletePassword.isNotBlank() && deletePhrase.trim() == "حذف حساب",
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val current = session ?: throw IllegalStateException("نشست کاربر پیدا نشد.")
                                    val result = NativeEx1Api.deleteAccount(current, deletePassword, deletePhrase.trim())
                                    if (!result.optBoolean("success")) {
                                        throw IllegalStateException(result.optString("error", "حذف حساب انجام نشد."))
                                    }
                                    NativeEx1Api.clearSession(context)
                                    session = null
                                    rooms = emptyList(); profile = null; wallet = null; permissions = emptyList(); selectedRoom = null; roomResults = emptyList(); roomSlots = emptyList(); soloStatus = null; roomAccess = null; team = null; teamInvites = emptyList(); sentTeamInvites = emptyList(); notifications = emptyList(); supportMessages = emptyList(); supportConversationId = null; supportFresh = false; adminSnapshot = NativeAdminSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
                                    showDeleteForm = false
                                    error = null
                                    deletePassword = ""
                                    deletePhrase = ""
                                } catch (e: Exception) {
                                    error = e.message ?: "حذف حساب انجام نشد."
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) { Text(if (busy) "در حال حذف..." else "حذف دائمی حساب") }
                },
                dismissButton = {
                    TextButton(onClick = { if (!busy) showDeleteForm = false }) { Text("انصراف") }
                }
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
                            rooms = emptyList(); profile = null; explorePlayers = emptyList(); exploreQuery = ""; selectedPublicProfile = null; publicProfileLoading = false; publicProfileError = null; wallet = null; permissions = emptyList(); selectedRoom = null; roomResults = emptyList(); roomSlots = emptyList(); soloStatus = null; roomAccess = null; team = null; teamInvites = emptyList(); sentTeamInvites = emptyList(); notifications = emptyList(); supportMessages = emptyList(); supportConversationId = null; supportFresh = false; adminSnapshot = NativeAdminSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
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
    explorePlayers: List<NativePublicProfile>,
    exploreQuery: String,
    onExploreQuery: (String) -> Unit,
    onSearchExplore: () -> Unit,
    onOpenPublicProfile: (String) -> Unit,
    wallet: NativeWallet?,
    activeTab: String,
    onTab: (String) -> Unit,
    onOpenRoom: (NativeRoom) -> Unit,
    notifications: List<NativeNotification>,
    supportMessages: List<NativeSupportMessage>,
    supportHint: String,
    supportError: String?,
    supportHandoff: Boolean,
    supportBusy: Boolean,
    supportSendRevision: Int,
    onNewSupport: () -> Unit,
    onSendSupport: (String) -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onDeleteNotification: (String) -> Unit,
    team: NativeTeam?,
    teamBusy: Boolean,
    teamInvites: List<NativeTeamInvite>,
    sentTeamInvites: List<NativeSentTeamInvite>,
    teamMessage: String?,
    teamError: String?,
    onCreateTeam: (String, String, String) -> Unit,
    onUpdateTeam: (String, String, String) -> Unit,
    onInvite: (String) -> Unit,
    onRespondInvite: (String, Boolean) -> Unit,
    onCancelInvite: (String) -> Unit,
    onTeamAction: (String, String?) -> Unit,
    onDeleteAccount: () -> Unit,
    onSaveProfile: (String, String, String, String, String) -> Unit,
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
            FilterChip(selected = activeTab == "explore", onClick = { onTab("explore") }, label = { Text("اکسپلور") })
            FilterChip(selected = activeTab == "profile", onClick = { onTab("profile") }, label = { Text("پروفایل") })
            FilterChip(selected = activeTab == "wallet", onClick = { onTab("wallet") }, label = { Text("کیف پول مجازی") })
            FilterChip(selected = activeTab == "team", onClick = { onTab("team") }, label = { Text("تیم") })
            FilterChip(selected = activeTab == "notifications", onClick = { onTab("notifications") }, label = { Text("اعلان‌ها") })
            FilterChip(selected = activeTab == "support", onClick = { onTab("support") }, label = { Text("پشتیبانی") })
            FilterChip(selected = activeTab == "settings", onClick = { onTab("settings") }, label = { Text("تنظیمات") })
        }
        if (!error.isNullOrBlank()) Text(error, color = if (error.contains("ذخیره شد") || error.contains("بررسی ارسال")) Color(0xFFBFA0F4) else Color(0xFFFFA2A2), modifier = Modifier.padding(vertical = 8.dp))
        when (activeTab) {
            "profile" -> ProfilePreviewPanel(profile = profile, onSave = onSaveProfile)
            "explore" -> ExplorePreviewPanel(explorePlayers, exploreQuery, onExploreQuery, onSearchExplore, onOpenPublicProfile)
            "wallet" -> WalletPreviewPanel(wallet = wallet)
            "notifications" -> NotificationsPreviewPanel(notifications, teamBusy, onMarkNotificationRead, onDeleteNotification)
            "support" -> SupportPreviewPanel(supportMessages, supportHint, supportError, supportHandoff, supportBusy, supportSendRevision, onNewSupport, onSendSupport)
            "team" -> TeamPreviewPanel(
                team = team,
                invites = teamInvites,
                sentInvites = sentTeamInvites,
                message = teamMessage,
                error = teamError,
                busy = teamBusy,
                onCreateTeam = onCreateTeam,
                onUpdateTeam = onUpdateTeam,
                onInvite = onInvite,
                onRespondInvite = onRespondInvite,
                onCancelInvite = onCancelInvite,
                onTeamAction = onTeamAction
            )
            "settings" -> SettingsPreviewPanel(onDeleteAccount = onDeleteAccount)
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
                        items(visibleRooms, key = { it.id }) { room -> RoomPreviewCard(room, onOpen = { onOpenRoom(room) }) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("خروج از حساب") }
    }
}

@Composable
private fun ExplorePreviewPanel(
    players: List<NativePublicProfile>,
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenProfile: (String) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("اکسپلور بازیکنان", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("بازیکنان جامعه EX1 را پیدا کن و بیوگرافی‌شان را ببین.", color = Color(0xFF9B91AA), fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f),
                label = { Text("نام یا نام کاربری") },
                singleLine = true
            )
            Button(onClick = onSearch) { Text("جست‌وجو") }
        }
        if (players.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("بازیکنی پیدا نشد. نامی جست‌وجو کن.", color = Color(0xFF9B91AA))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(players, key = { it.id }) { player ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF191426))) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                Modifier.size(42.dp).background(Color(0xFF392450), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text((player.displayName ?: "EX").take(1), fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(player.displayName ?: "بازیکن", fontWeight = FontWeight.Bold)
                                Text(player.username?.let { "@$it" } ?: "بازیکن EX1", color = Color(0xFFBBA8D8), fontSize = 11.sp)
                                if (!player.bio.isNullOrBlank()) {
                                    Text(player.bio, color = Color(0xFF9B91AA), fontSize = 11.sp, maxLines = 2)
                                }
                            }
                            TextButton(onClick = { onOpenProfile(player.id) }) { Text("پروفایل") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PublicProfileDialog(
    profile: NativePublicProfile?,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("پروفایل بازیکن") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (loading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                if (!error.isNullOrBlank()) Text(error, color = Color(0xFFFFA2A2))
                if (profile != null) {
                    Box(
                        Modifier.size(68.dp).background(Color(0xFF392450), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text((profile.displayName ?: "EX").take(1), fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }
                    Text(profile.displayName ?: "بازیکن", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(profile.username?.let { "@$it" } ?: "بازیکن EX1", color = Color(0xFFBBA8D8), fontSize = 12.sp)
                    profile.roleBadge?.let {
                        Text(if (it == "owner") "✦ مالک EX1" else "◆ مدیر EX1", color = Color(0xFFC9A5FF), fontSize = 11.sp)
                    }
                    if (!profile.gameName.isNullOrBlank()) {
                        Text("نام داخل بازی: ${profile.gameName}", color = Color(0xFFBBA8D8), fontSize = 12.sp)
                    }
                    Text("بیوگرافی", fontWeight = FontWeight.Bold)
                    Text(
                        profile.bio?.takeIf { it.isNotBlank() } ?: "این بازیکن هنوز بیوگرافی ننوشته است.",
                        color = Color(0xFFD8D0E4),
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("بستن") } }
    )
}

@Composable
private fun ProfilePreviewPanel(
    profile: NativeProfile?,
    onSave: (String, String, String, String, String) -> Unit
) {
    var displayName by remember(profile?.id, profile?.displayName) { mutableStateOf(profile?.displayName.orEmpty()) }
    var username by remember(profile?.id, profile?.username) { mutableStateOf(profile?.username.orEmpty()) }
    var gameUid by remember(profile?.id, profile?.gameUid) { mutableStateOf(profile?.gameUid.orEmpty()) }
    var gameName by remember(profile?.id, profile?.gameName) { mutableStateOf(profile?.gameName.orEmpty()) }
    var bio by remember(profile?.id, profile?.bio) { mutableStateOf(profile?.bio.orEmpty()) }
    Column(
        Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("اطلاعات پروفایل", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        OutlinedTextField(displayName, { displayName = it }, label = { Text("نام نمایشی") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(username, { username = it }, label = { Text("نام کاربری") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(gameUid, { gameUid = it }, label = { Text("UID کالاف") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(gameName, { gameName = it }, label = { Text("نام داخل بازی") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(
            bio,
            { bio = it.take(500) },
            label = { Text("بیوگرافی عمومی") },
            placeholder = { Text("کمی درباره خودت و سبک بازیت بنویس...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            supportingText = { Text("${bio.length}/500") }
        )
        Text("بیوگرافی پس از بررسی ایمنی در پروفایل عمومی نمایش داده می‌شود.", color = Color(0xFF9B91AA), fontSize = 12.sp)
        Button(
            onClick = { onSave(displayName, username, gameUid, gameName, bio) },
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
private fun SupportPreviewPanel(
    messages: List<NativeSupportMessage>,
    hint: String,
    error: String?,
    handoff: Boolean,
    busy: Boolean,
    sendRevision: Int,
    onNewConversation: () -> Unit,
    onSend: (String) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    LaunchedEffect(sendRevision) { if (sendRevision > 0) draft = "" }
    Column(
        Modifier.fillMaxWidth().heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("پشتیبانی EX1", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            TextButton(enabled = !busy, onClick = { draft = ""; onNewConversation() }) { Text("گفت‌وگوی تازه") }
        }
        if (handoff) Text("این گفت‌وگو در صف پشتیبان انسانی قرار دارد.", color = Color(0xFFBFA0F4), fontSize = 12.sp)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item { Text("برای شروع، پیام خودت را بنویس.", color = Color(0xFF9B91AA)) }
            }
            items(messages) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (item.senderRole == "user") Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.senderRole == "user") Color(0xFF382653) else Color(0xFF191426)
                        )
                    ) {
                        Text(item.body, modifier = Modifier.padding(12.dp), color = Color(0xFFF4F0FA))
                    }
                }
            }
        }
        if (!error.isNullOrBlank()) Text(error, color = Color(0xFFFF9B9B), fontSize = 12.sp)
        Text(hint, color = Color(0xFF9B91AA), fontSize = 11.sp)
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("پیامت را بنویس") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )
        Button(
            onClick = {
                val message = draft.trim()
                if (message.isNotEmpty()) {
                    onSend(message)
                }
            },
            enabled = !busy && draft.trim().isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("ارسال پیام") }
    }
}

@Composable
private fun NotificationsPreviewPanel(
    notifications: List<NativeNotification>,
    busy: Boolean,
    onMarkRead: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("اعلان‌های حساب", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (notifications.isEmpty()) {
            Text("اعلان تازه‌ای وجود ندارد.", color = Color(0xFF9B91AA))
        } else {
            notifications.forEach { item ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (item.readAt == null) Color(0xFF211831) else Color(0xFF171421)
                    )
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(item.title, fontWeight = FontWeight.Bold)
                        Text(item.body, color = Color(0xFFC4BBD2), fontSize = 13.sp)
                        Text(item.createdAt, color = Color(0xFF8E879B), fontSize = 10.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (item.readAt == null) {
                                TextButton(enabled = !busy, onClick = { onMarkRead(item.id) }) { Text("خواندم") }
                            } else {
                                Text("خوانده‌شده", color = Color(0xFF8E879B), fontSize = 11.sp, modifier = Modifier.padding(10.dp))
                            }
                            TextButton(enabled = !busy, onClick = { pendingDelete = item.id }) { Text("حذف اعلان") }
                        }
                    }
                }
            }
        }
    }
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف اعلان") },
            text = { Text("این اعلان فقط از فهرست حساب خودت حذف شود؟") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    pendingDelete?.let(onDelete)
                    pendingDelete = null
                }) { Text("حذف اعلان") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("انصراف") }
            }
        )
    }
}

@Composable
private fun TeamPreviewPanel(
    team: NativeTeam?,
    invites: List<NativeTeamInvite>,
    sentInvites: List<NativeSentTeamInvite>,
    message: String?,
    error: String?,
    busy: Boolean,
    onCreateTeam: (String, String, String) -> Unit,
    onUpdateTeam: (String, String, String) -> Unit,
    onInvite: (String) -> Unit,
    onRespondInvite: (String, Boolean) -> Unit,
    onCancelInvite: (String) -> Unit,
    onTeamAction: (String, String?) -> Unit
) {
    var name by remember(team?.id, team?.name) { mutableStateOf(team?.name.orEmpty()) }
    var tag by remember(team?.id, team?.tag) { mutableStateOf(team?.tag.orEmpty()) }
    var description by remember(team?.id, team?.description) { mutableStateOf(team?.description.orEmpty()) }
    var inviteUsername by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<String?>(null) }
    var pendingUserId by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("تیم من", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (!message.isNullOrBlank()) Text(message, color = Color(0xFF9DE3B2), fontSize = 12.sp)
        if (!error.isNullOrBlank()) Text(error, color = Color(0xFFFF9B9B), fontSize = 12.sp)

        if (team == null) {
            Text("هنوز عضو تیمی نیستی. می‌توانی تیم خودت را بسازی.", color = Color(0xFFBBA8D8), fontSize = 12.sp)
            OutlinedTextField(name, { name = it }, label = { Text("نام تیم") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(tag, { tag = it }, label = { Text("تگ تیم (اختیاری)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(description, { description = it }, label = { Text("توضیحات (اختیاری)") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { onCreateTeam(name, tag, description) },
                enabled = !busy && name.trim().length >= 2,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "در حال ساخت..." else "ساخت تیم") }
        } else {
            Text(team.name, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            if (!team.tag.isNullOrBlank()) Text("تگ: ${team.tag}", color = Color(0xFFBBA8D8))
            Text(team.description ?: "تیم بازیکنان EX1", color = Color(0xFF9B91AA), fontSize = 12.sp)
            Text("${team.members.size} از ۴ عضو", color = Color(0xFFBBA8D8), fontSize = 12.sp)
            team.members.forEach { member ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF191426))) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(member.displayName, fontWeight = FontWeight.Bold)
                            Text(
                                if (member.userId == team.captainId) "کاپیتان" else "عضو تیم",
                                color = Color(0xFF9B91AA),
                                fontSize = 11.sp
                            )
                        }
                        if (team.isCaptain && member.userId != team.captainId) {
                            TextButton(enabled = !busy, onClick = {
                                pendingUserId = member.userId
                                pendingAction = "remove"
                            }) { Text("اخراج") }
                        }
                    }
                }
            }
            if (team.isCaptain) {
                Text("مدیریت تیم", fontWeight = FontWeight.Bold)
                OutlinedTextField(name, { name = it }, label = { Text("نام تیم") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(tag, { tag = it }, label = { Text("تگ تیم") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("توضیحات") }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { onUpdateTeam(name, tag, description) },
                    enabled = !busy && name.trim().length >= 2,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ذخیره تغییرات تیم") }
                OutlinedTextField(
                    inviteUsername,
                    { inviteUsername = it },
                    label = { Text("نام کاربری بازیکن برای دعوت") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = { onInvite(inviteUsername); inviteUsername = "" },
                    enabled = !busy && inviteUsername.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ارسال دعوت تیم") }
                if (sentInvites.isNotEmpty()) {
                    Text("دعوت‌های در انتظار پاسخ", fontWeight = FontWeight.Bold)
                    sentInvites.forEach { invite ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(invite.displayName)
                                Text(invite.username?.let { "@$it" } ?: "بدون نام کاربری", color = Color(0xFF9B91AA), fontSize = 11.sp)
                            }
                            TextButton(enabled = !busy, onClick = { onCancelInvite(invite.id) }) { Text("لغو") }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(enabled = !busy, onClick = { pendingAction = "leave" }) { Text("خروج از تیم") }
                if (team.isCaptain) {
                    TextButton(enabled = !busy, onClick = { pendingAction = "delete" }) { Text("حذف کامل تیم") }
                }
            }
        }

        if (invites.isNotEmpty()) {
            Text("دعوت‌های دریافتی", fontWeight = FontWeight.Bold)
            invites.forEach { invite ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF211831))) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(invite.teamName, fontWeight = FontWeight.Bold)
                        Text("دعوت از ${invite.inviterName}", color = Color(0xFFBBA8D8), fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(enabled = !busy, onClick = { onRespondInvite(invite.id, true) }) { Text("پذیرش") }
                            TextButton(enabled = !busy, onClick = { onRespondInvite(invite.id, false) }) { Text("رد دعوت") }
                        }
                    }
                }
            }
        }
    }

    if (pendingAction != null) {
        val action = pendingAction!!
        AlertDialog(
            onDismissRequest = { pendingAction = null; pendingUserId = null },
            title = { Text(when (action) { "leave" -> "خروج از تیم"; "delete" -> "حذف تیم"; else -> "حذف عضو" }) },
            text = {
                Text(
                    when (action) {
                        "leave" -> "از تیم خارج می‌شوی؟ انتقال کاپیتانی طبق قوانین سرور انجام می‌شود."
                        "delete" -> "تیم به‌طور کامل حذف شود؟ سوابق مسابقه‌ای دارای نتیجه قابل حذف نیستند."
                        else -> "این بازیکن از تیم خارج شود؟"
                    }
                )
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    onTeamAction(action, pendingUserId)
                    pendingAction = null
                    pendingUserId = null
                }) { Text("تأیید") }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null; pendingUserId = null }) { Text("انصراف") }
            }
        )
    }
}

@Composable
private fun SettingsPreviewPanel(onDeleteAccount: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("تنظیمات حساب", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("خروج از حساب و حذف حساب دو عملیات جدا هستند. حذف حساب فقط پس از تأیید دوباره و بررسی رمز فعلی توسط سرور انجام می‌شود.", color = Color(0xFF9B91AA), fontSize = 12.sp)
        Button(onClick = onDeleteAccount, modifier = Modifier.fillMaxWidth()) {
            Text("حذف دائمی حساب کاربری")
        }
    }
}

@Composable
private fun RoomDetailDialog(
    room: NativeRoom,
    slots: List<NativeRoomSlot>,
    results: List<NativePlayerResult>,
    soloStatus: NativeSoloStatus?,
    access: NativeRoomAccess?,
    loading: Boolean,
    busy: Boolean,
    error: String?,
    message: String?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onRegister: (Int, Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val maxTeams = room.maxTeams.takeIf { it > 0 } ?: if (room.teamSize > 0) room.capacity / room.teamSize else 0
    val occupied = slots.associateBy { it.number }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(room.title) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("ورودی: ${room.entryPoints} امتیاز مجازی · ظرفیت: ${room.capacity}")
                Text("جایزه اول: ${room.firstPrize} امتیاز مجازی · هر تیم: ${room.teamSize} نفر", color = Color(0xFFBBA8D8), fontSize = 12.sp)
                if (loading) CircularProgressIndicator()
                if (!message.isNullOrBlank()) Text(message, color = Color(0xFF9DE3B2))
                if (!error.isNullOrBlank()) Text(error, color = Color(0xFFFF9B9B))
                if (soloStatus?.registered == true) {
                    Text(
                        if (soloStatus.status == "matched") "تیم تو تشکیل شده است · جایگاه ${soloStatus.slotNumber ?: "—"}"
                        else "در صف تشکیل تیم هستی · ${soloStatus.waiting ?: 0} نفر حاضر، ${soloStatus.needed ?: 0} نفر دیگر لازم است.",
                        color = Color(0xFFBFA0F4)
                    )
                }
                if (access?.accessReady == true) {
                    Text("اطلاعات روم بازی", fontWeight = FontWeight.Bold)
                    access.roomCode?.let { code ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("کد: $code", modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(ClipData.newPlainText("EX1 room code", code))
                                Toast.makeText(context, "کد روم کپی شد.", Toast.LENGTH_SHORT).show()
                            }) { Text("کپی") }
                        }
                    }
                    access.roomPassword?.let { password ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("رمز: $password", modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(ClipData.newPlainText("EX1 room password", password))
                                Toast.makeText(context, "رمز روم کپی شد.", Toast.LENGTH_SHORT).show()
                            }) { Text("کپی") }
                        }
                    }
                } else if (access?.waitingForStart == true || access?.waitingForAdmin == true) {
                    Text("اطلاعات خصوصی روم پس از زمان‌بندی و تأیید مدیر برای شرکت‌کنندگان مجاز نمایش داده می‌شود.", color = Color(0xFFBBA8D8), fontSize = 12.sp)
                }
                TextButton(onClick = onRefresh, enabled = !busy && !loading) { Text("به‌روزرسانی وضعیت و اطلاعات روم") }
                Text("جایگاه‌های تیمی", fontWeight = FontWeight.Bold)
                if (isRoomFinished(room)) {
                    Text("نتایج نهایی", fontWeight = FontWeight.Bold)
                    if (results.isEmpty()) Text("هنوز نتیجه‌ای برای این روم ثبت نشده است.", color = Color(0xFF9B91AA))
                    else results.forEach { result ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("#${result.placement ?: "—"}", modifier = Modifier.width(34.dp), fontWeight = FontWeight.Bold)
                            Text(result.playerName, modifier = Modifier.weight(1f))
                            Text("کیل ${result.kills} · امتیاز ${result.points}", color = Color(0xFFBBA8D8), fontSize = 11.sp)
                        }
                    }
                }
                if (maxTeams <= 0) Text("ظرفیت جایگاه‌ها مشخص نیست.")
                else if (!isRoomRegistrationOpen(room)) Text("مهلت ثبت‌نام این روم باز نیست.")
                else (1..maxTeams).forEach { slotNumber ->
                    val slot = occupied[slotNumber]
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF191426))) {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(
                                when {
                                    slot == null -> "جایگاه $slotNumber · آزاد"
                                    slot.status == "waiting" -> "جایگاه $slotNumber · در حال تشکیل تیم"
                                    else -> "جایگاه $slotNumber · ${slot.teamName.ifBlank { "تیم ثبت‌شده" }}"
                                },
                                fontWeight = FontWeight.Bold
                            )
                            if (slot != null && slot.players.isNotEmpty()) {
                                Text(slot.players.joinToString("، "), color = Color(0xFFBBA8D8), fontSize = 11.sp)
                            }
                            if (slot == null || slot.status == "waiting") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(enabled = !busy, onClick = { onRegister(slotNumber, false) }) { Text("ثبت‌نام تیمی") }
                                    if (room.teamSize > 1) TextButton(enabled = !busy, onClick = { onRegister(slotNumber, true) }) { Text("ثبت‌نام انفرادی") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("بستن") } }
    )
}

@Composable
private fun RoomPreviewCard(room: NativeRoom, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isRoomFinished(room)) Color(0xFF121018) else Color(0xFF191426))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(room.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (isRoomFinished(room)) Color(0xFF8E879B) else Color.White)
                Text(
                    when {
                        isRoomFinished(room) -> "پایان‌یافته"
                        room.status == "open" -> "باز"
                        room.status == "full" -> "تکمیل"
                        else -> room.status
                    },
                    color = if (isRoomFinished(room)) Color(0xFF898393) else Color(0xFFC5A4FF),
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("ورودی: ${room.entryPoints} امتیاز مجازی  ·  ظرفیت: ${room.capacity}", color = Color(0xFFC4BBD2), fontSize = 13.sp)
            Text("جایزه اول: ${room.firstPrize} امتیاز مجازی", color = Color(0xFFBFA0F4), fontSize = 13.sp)
            val meta = listOfNotNull(room.gameMode, room.map).joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, color = Color(0xFF8E879B), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("جزئیات و جایگاه‌ها") }
        }
    }
}

@Composable
private fun AdminPreviewPanel(
    permissions: List<String>,
    snapshot: NativeAdminSnapshot,
    ownerAccessDenied: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    var section by remember { mutableStateOf("overview") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("EX1 LEGEND", fontWeight = FontWeight.Black, fontSize = 23.sp)
                Text("پنل مالک · پیش‌نمایش Native", color = Color(0xFFBBA8D8), fontSize = 12.sp)
            }
            Button(onClick = onRefresh) { Text("به‌روزرسانی") }
        }
        Spacer(Modifier.height(12.dp))
        if (ownerAccessDenied) {
            Text("این بخش فقط برای حساب مالک اصلی در دسترس است.", color = Color(0xFFFFA2A2))
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                listOf(
                    "overview" to "خلاصه",
                    "tournaments" to "مسابقات",
                    "registrations" to "ثبت‌نام‌ها",
                    "teams" to "تیم‌ها",
                    "users" to "کاربران",
                    "wallet" to "امتیازهای مجازی",
                    "permissions" to "مجوزها"
                ).forEach { (key, label) ->
                    FilterChip(selected = section == key, onClick = { section = key }, label = { Text(label, fontSize = 11.sp) })
                }
            }
            when (section) {
                "overview" -> Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AdminMetricCard("مسابقات", snapshot.tournaments.size)
                    AdminMetricCard("ثبت‌نام‌ها", snapshot.registrations.size)
                    AdminMetricCard("تیم‌ها", snapshot.teams.size)
                    AdminMetricCard("کاربران", snapshot.users.size)
                    AdminMetricCard("کیف‌پول‌های مجازی", snapshot.wallet.size)
                    Text("داده‌ها فقط از RPCهای مجاز و با بررسی مجوز سمت سرور خوانده می‌شوند.", color = Color(0xFF9B91AA), fontSize = 11.sp)
                }
                "tournaments" -> if (permissions.contains("tournaments.view")) {
                    AdminDataPanel("مسابقات", snapshot.tournaments, listOf(
                        "عنوان" to "title", "بازی" to "game", "حالت" to "mode",
                        "نقشه" to "map", "وضعیت" to "status", "شروع" to "starts_at"
                    ))
                } else AdminNoPermission()
                "registrations" -> if (permissions.contains("registrations.view_all")) {
                    AdminDataPanel("ثبت‌نام‌ها", snapshot.registrations, listOf(
                        "مسابقه" to "tournament_title", "تیم" to "team_name",
                        "برچسب" to "team_tag", "وضعیت" to "status", "زمان" to "registered_at"
                    ))
                } else AdminNoPermission()
                "teams" -> if (permissions.contains("teams.view")) {
                    AdminDataPanel("تیم‌ها", snapshot.teams, listOf(
                        "نام تیم" to "name", "تگ" to "tag", "اعضا" to "member_count", "توضیحات" to "description"
                    ))
                } else AdminNoPermission()
                "users" -> if (permissions.contains("users.view") || permissions.contains("profiles.view_all")) {
                    AdminDataPanel("کاربران", snapshot.users, listOf(
                        "نام" to "display_name", "نام کاربری" to "username", "شناسه" to "user_id", "تاریخ" to "created_at"
                    ))
                } else AdminNoPermission()
                "wallet" -> if (permissions.contains("wallet.view_all")) {
                    AdminDataPanel("موجودی‌های امتیاز مجازی · فقط خواندنی", snapshot.wallet, listOf(
                        "نام" to "display_name", "نام بازی" to "game_name", "شناسه" to "user_id",
                        "امتیاز" to "balance_units", "دفترکل" to "currency_code", "حالت" to "wallet_mode"
                    ))
                } else AdminNoPermission()
                else -> Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("مجوزهای فعال حساب", fontWeight = FontWeight.Bold)
                    permissions.forEach { Text("• $it", color = Color(0xFFD9CDEA), fontSize = 12.sp) }
                }
            }
        }
        if (!error.isNullOrBlank()) Text(error, color = Color(0xFFFFA2A2), modifier = Modifier.padding(vertical = 6.dp))
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("خروج از حساب") }
    }
}

@Composable
private fun AdminMetricCard(label: String, value: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF191426))) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), color = Color(0xFFBBA8D8))
            Text(value.toString(), fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
    }
}

@Composable
private fun AdminNoPermission() {
    Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
        Text("مجوز مشاهده این بخش برای حساب فعال نیست.", color = Color(0xFF9B91AA))
    }
}

@Composable
private fun AdminDataPanel(title: String, rows: List<org.json.JSONObject>, fields: List<Pair<String, String>>) {
    Column(
        Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("$title · ${rows.size}", fontWeight = FontWeight.Bold)
        if (rows.isEmpty()) {
            Text("داده‌ای برای نمایش وجود ندارد.", color = Color(0xFF9B91AA))
        } else {
            rows.forEach { row ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF191426))) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        fields.forEachIndexed { index, field ->
                            val value = row.optString(field.second).takeIf { it.isNotBlank() && it != "null" } ?: "—"
                            Text(
                                "${field.first}: $value",
                                color = if (index == 0) Color.White else Color(0xFFBBA8D8),
                                fontSize = if (index == 0) 14.sp else 11.sp,
                                fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

