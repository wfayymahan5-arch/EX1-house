package com.ex1legend.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Ex1Session(val accessToken: String, val refreshToken: String, val userId: String, val email: String)
data class Ex1Profile(val id: String, val username: String?, val displayName: String?, val avatarUrl: String?, val gameUid: String?, val gameName: String?)
data class Ex1Room(
    val id: String, val title: String, val gameMode: String?, val map: String?, val capacity: Int,
    val startsAt: String?, val endsAt: String?, val status: String, val teamSize: Int, val maxTeams: Int,
    val entryPoints: Long, val firstPrize: Long, val secondPrize: Long, val thirdPrize: Long
)
data class WalletEntry(val type: String, val amount: Long, val balanceAfter: Long, val reason: String, val createdAt: String)
data class Ex1Wallet(val balance: Long, val history: List<WalletEntry>)
data class Ex1RoomSlot(val number: Int, val teamName: String, val status: String, val players: List<String>)
data class Ex1SoloStatus(val registered: Boolean, val status: String?, val slotNumber: Int?, val waiting: Int?, val needed: Int?)
class Ex1ApiException(val statusCode: Int, message: String) : Exception(message)

private object SessionVault {
    private const val PREFS = "ex1_native_session"
    private const val VALUE = "encrypted_session"
    private const val KEY_ALIAS = "ex1_native_session_key"

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    fun save(context: Context, session: Ex1Session) {
        val plain = JSONObject().put("access", session.accessToken).put("refresh", session.refreshToken)
            .put("user", session.userId).put("email", session.email).toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val packed = ByteArrayOutputStream().apply { write(cipher.iv); write(cipher.doFinal(plain)) }.toByteArray()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(VALUE, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun load(context: Context): Ex1Session? {
        return try {
            val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(VALUE, null) ?: return null
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            if (packed.size <= 12) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
            val json = JSONObject(String(cipher.doFinal(packed.copyOfRange(12, packed.size)), Charsets.UTF_8))
            Ex1Session(json.getString("access"), json.getString("refresh"), json.getString("user"), json.optString("email"))
        } catch (_: Exception) {
            clear(context)
            null
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(VALUE).apply()
    }
}

object Ex1Api {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()

    private fun request(method: String, path: String, session: Ex1Session? = null, payload: JSONObject? = null): Any? {
        val builder = Request.Builder().url(BuildConfig.SUPABASE_URL.trimEnd('/') + path)
            .header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY).header("Accept", "application/json")
        if (session != null) builder.header("Authorization", "Bearer ${session.accessToken}")
        val body = payload?.toString()?.toRequestBody(jsonType)
        when (method) {
            "POST" -> builder.post(body ?: "{}".toRequestBody(jsonType))
            "PATCH" -> builder.patch(body ?: "{}".toRequestBody(jsonType))
            "DELETE" -> builder.delete(body)
            else -> builder.get()
        }
        http.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = try {
                    val error = JSONObject(raw)
                    error.optString("msg").ifBlank { error.optString("message").ifBlank {
                        error.optString("error_description").ifBlank { error.optString("error").ifBlank { error.optString("details", "درخواست ناموفق بود.") } }
                    } }
                } catch (_: Exception) { "ارتباط با سرور ناموفق بود." }
                throw Ex1ApiException(response.code, message)
            }
            if (raw.isBlank()) return null
            return JSONTokener(raw).nextValue()
        }
    }

    private fun sessionFrom(json: JSONObject): Ex1Session? {
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        val user = json.optJSONObject("user") ?: return null
        val id = user.optString("id")
        if (access.isBlank() || refresh.isBlank() || id.isBlank()) return null
        return Ex1Session(access, refresh, id, user.optString("email"))
    }

    suspend fun signIn(email: String, password: String): Ex1Session = withContext(Dispatchers.IO) {
        val response = request("POST", "/auth/v1/token?grant_type=password",
            payload = JSONObject().put("email", email).put("password", password)) as? JSONObject
            ?: throw IllegalStateException("پاسخ ورود معتبر نیست.")
        sessionFrom(response) ?: throw IllegalStateException("سرور نشست ورود را برنگرداند.")
    }

    suspend fun signUp(email: String, password: String, displayName: String): Ex1Session? = withContext(Dispatchers.IO) {
        val response = request("POST", "/auth/v1/signup", payload = JSONObject()
            .put("email", email).put("password", password).put("data", JSONObject().put("display_name", displayName))) as? JSONObject
            ?: throw IllegalStateException("پاسخ ثبت‌نام معتبر نیست.")
        sessionFrom(response)
    }

    suspend fun refresh(session: Ex1Session): Ex1Session = withContext(Dispatchers.IO) {
        val response = request("POST", "/auth/v1/token?grant_type=refresh_token",
            payload = JSONObject().put("refresh_token", session.refreshToken)) as? JSONObject
            ?: throw IllegalStateException("پاسخ تمدید نشست معتبر نیست.")
        sessionFrom(response) ?: throw IllegalStateException("تمدید نشست انجام نشد.")
    }

    suspend fun profile(session: Ex1Session): Ex1Profile? = withContext(Dispatchers.IO) {
        val path = "/rest/v1/profiles?select=id,username,display_name,avatar_url,game_uid,game_name&id=eq.${session.userId}&limit=1"
        val rows = request("GET", path, session) as? JSONArray ?: return@withContext null
        if (rows.length() == 0) return@withContext null
        val row = rows.getJSONObject(0)
        fun opt(name: String) = row.optString(name).takeIf { it.isNotBlank() && it != "null" }
        Ex1Profile(row.optString("id"), opt("username"), opt("display_name"), opt("avatar_url"), opt("game_uid"), opt("game_name"))
    }

    suspend fun rooms(session: Ex1Session?): List<Ex1Room> = withContext(Dispatchers.IO) {
        val path = "/rest/v1/tournament_rooms?select=id,title,game_mode,map,capacity,starts_at,ends_at,status,team_size,max_teams,entry_demo_points,prize_demo_points,prize_second_demo_points,prize_third_demo_points&status=in.(open,full,completed)&order=starts_at.desc&limit=100"
        val rows = request("GET", path, session) as? JSONArray ?: JSONArray()
        (0 until rows.length()).map { i ->
            val r = rows.getJSONObject(i)
            fun opt(name: String) = r.optString(name).takeIf { it.isNotBlank() && it != "null" }
            Ex1Room(r.optString("id"), r.optString("title"), opt("game_mode"), opt("map"), r.optInt("capacity"),
                opt("starts_at"), opt("ends_at"), r.optString("status"), r.optInt("team_size", 4), r.optInt("max_teams"),
                r.optLong("entry_demo_points"), r.optLong("prize_demo_points"), r.optLong("prize_second_demo_points"), r.optLong("prize_third_demo_points"))
        }
    }

    suspend fun roomSlots(session: Ex1Session, roomId: String): List<Ex1RoomSlot> = withContext(Dispatchers.IO) {
        val rows = request("POST", "/rest/v1/rpc/get_tournament_room_slots", session,
            JSONObject().put("p_room_id", roomId)) as? JSONArray ?: JSONArray()
        (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            val playersJson = row.optJSONArray("players") ?: JSONArray()
            val players = (0 until playersJson.length()).mapNotNull { j ->
                val p = playersJson.optJSONObject(j) ?: return@mapNotNull null
                p.optString("display_name").takeIf { it.isNotBlank() && it != "null" }
                    ?: p.optString("game_name").takeIf { it.isNotBlank() && it != "null" }
            }
            Ex1RoomSlot(row.optInt("slot_number"), row.optString("team_name"), row.optString("status"), players)
        }
    }

    suspend fun mySoloStatus(session: Ex1Session, roomId: String): Ex1SoloStatus = withContext(Dispatchers.IO) {
        val result = request("POST", "/rest/v1/rpc/get_my_room_solo_status", session,
            JSONObject().put("p_room_id", roomId)) as? JSONObject ?: JSONObject()
        Ex1SoloStatus(result.optBoolean("registered"), result.optString("status").takeIf { it.isNotBlank() },
            result.optInt("slot_number").takeIf { result.has("slot_number") && !result.isNull("slot_number") },
            result.optInt("players_waiting").takeIf { result.has("players_waiting") },
            result.optInt("players_needed").takeIf { result.has("players_needed") })
    }

    suspend fun registerSolo(session: Ex1Session, roomId: String, slot: Int): JSONObject = withContext(Dispatchers.IO) {
        request("POST", "/rest/v1/rpc/register_for_tournament_room_solo", session,
            JSONObject().put("p_room_id", roomId).put("p_slot_number", slot)) as? JSONObject
            ?: throw IllegalStateException("پاسخ ثبت‌نام معتبر نیست.")
    }

    suspend fun registerTeam(session: Ex1Session, roomId: String, slot: Int): JSONObject = withContext(Dispatchers.IO) {
        request("POST", "/rest/v1/rpc/register_for_tournament_room", session,
            JSONObject().put("p_room_id", roomId).put("p_slot_number", slot)) as? JSONObject
            ?: throw IllegalStateException("پاسخ ثبت‌نام معتبر نیست.")
    }

    suspend fun wallet(session: Ex1Session): Ex1Wallet = withContext(Dispatchers.IO) {
        val summary = request("POST", "/rest/v1/rpc/wallet_my_summary", session, JSONObject()) as? JSONArray
        val first = if (summary != null && summary.length() > 0) summary.optJSONObject(0) else null
        val history = request("POST", "/rest/v1/rpc/wallet_my_history", session, JSONObject().put("p_limit", 30)) as? JSONArray ?: JSONArray()
        val entries = (0 until history.length()).map { i ->
            val r = history.getJSONObject(i)
            WalletEntry(r.optString("entry_type"), r.optLong("amount_units"), r.optLong("balance_after_units"), r.optString("reason"), r.optString("created_at"))
        }
        Ex1Wallet(first?.optLong("balance_units") ?: 0L, entries)
    }

    suspend fun adminPermissions(session: Ex1Session): List<String> = withContext(Dispatchers.IO) {
        val rows = request("POST", "/rest/v1/rpc/admin_my_permissions", session, JSONObject()) as? JSONArray
            ?: return@withContext emptyList()
        (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            if (row.optBoolean("is_allowed")) row.optString("permission_code").takeIf { it.isNotBlank() } else null
        }
    }

    suspend fun updateProfile(
        session: Ex1Session,
        displayName: String,
        username: String,
        gameUid: String,
        gameName: String
    ): JSONObject = withContext(Dispatchers.IO) {
        request(
            "POST",
            "/functions/v1/update-profile",
            session,
            JSONObject()
                .put("display_name", displayName)
                .put("username", username.ifBlank { JSONObject.NULL })
                .put("game_uid", gameUid.ifBlank { JSONObject.NULL })
                .put("game_name", gameName.ifBlank { JSONObject.NULL })
        ) as? JSONObject ?: throw IllegalStateException("پاسخ ذخیره پروفایل معتبر نیست.")
    }

    suspend fun signOut(session: Ex1Session) = withContext(Dispatchers.IO) {
        try { request("POST", "/auth/v1/logout", session, JSONObject()) } catch (_: Exception) { }
    }
}

fun saveSession(context: Context, session: Ex1Session) = SessionVault.save(context, session)
fun loadSession(context: Context): Ex1Session? = SessionVault.load(context)
fun clearSession(context: Context) = SessionVault.clear(context)
