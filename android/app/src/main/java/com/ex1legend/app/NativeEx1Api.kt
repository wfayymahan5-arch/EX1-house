package com.ex1legend.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

data class NativeSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String
)

data class NativeRoom(
    val id: String,
    val title: String,
    val gameCategory: String,
    val priceCategory: String,
    val gameMode: String?,
    val map: String?,
    val status: String,
    val startsAt: String?,
    val capacity: Int,
    val teamSize: Int,
    val entryPoints: Long,
    val firstPrize: Long
)

class NativeApiException(val status: Int, message: String) : Exception(message)

private object NativeSessionVault {
    private const val PREFS = "ex1_native_session"
    private const val VALUE = "encrypted_session"
    private const val KEY_ALIAS = "ex1_native_session_key"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    fun save(context: Context, session: NativeSession) {
        val plain = JSONObject()
            .put("access", session.accessToken)
            .put("refresh", session.refreshToken)
            .put("user", session.userId)
            .put("email", session.email)
            .toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val packed = ByteArrayOutputStream().apply {
            write(cipher.iv)
            write(cipher.doFinal(plain))
        }.toByteArray()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(VALUE, Base64.encodeToString(packed, Base64.NO_WRAP))
            .apply()
    }

    fun load(context: Context): NativeSession? {
        return try {
            val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(VALUE, null) ?: return null
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            if (packed.size <= 12) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(128, packed.copyOfRange(0, 12))
            )
            val json = JSONObject(
                String(cipher.doFinal(packed.copyOfRange(12, packed.size)), Charsets.UTF_8)
            )
            NativeSession(
                json.getString("access"),
                json.getString("refresh"),
                json.getString("user"),
                json.optString("email")
            )
        } catch (_: Exception) {
            clear(context)
            null
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(VALUE).apply()
    }
}

object NativeEx1Api {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    private fun request(
        method: String,
        path: String,
        session: NativeSession? = null,
        payload: JSONObject? = null
    ): Any? {
        val builder = Request.Builder()
            .url(BuildConfig.SUPABASE_URL.trimEnd('/') + path)
            .header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            .header("Accept", "application/json")
        if (session != null) builder.header("Authorization", "Bearer ${session.accessToken}")
        val body = payload?.toString()?.toRequestBody(jsonType)
        when (method) {
            "POST" -> builder.post(body ?: "{}".toRequestBody(jsonType))
            "PATCH" -> builder.patch(body ?: "{}".toRequestBody(jsonType))
            "DELETE" -> builder.delete(body)
            else -> builder.get()
        }
        client.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = try {
                    val error = JSONObject(raw)
                    error.optString("msg").ifBlank {
                        error.optString("message").ifBlank {
                            error.optString("error_description").ifBlank {
                                error.optString("details", "ارتباط با سرور ناموفق بود.")
                            }
                        }
                    }
                } catch (_: Exception) {
                    "ارتباط با سرور ناموفق بود."
                }
                throw NativeApiException(response.code, message)
            }
            if (raw.isBlank()) return null
            return JSONTokener(raw).nextValue()
        }
    }

    private fun parseSession(json: JSONObject): NativeSession? {
        val user = json.optJSONObject("user") ?: return null
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        val userId = user.optString("id")
        if (access.isBlank() || refresh.isBlank() || userId.isBlank()) return null
        return NativeSession(access, refresh, userId, user.optString("email"))
    }

    suspend fun signIn(email: String, password: String): NativeSession = withContext(Dispatchers.IO) {
        val result = request(
            "POST",
            "/auth/v1/token?grant_type=password",
            payload = JSONObject().put("email", email.trim()).put("password", password)
        ) as? JSONObject ?: throw IllegalStateException("پاسخ ورود معتبر نیست.")
        parseSession(result) ?: throw IllegalStateException("نشست ورود از سرور دریافت نشد.")
    }

    suspend fun refresh(session: NativeSession): NativeSession = withContext(Dispatchers.IO) {
        val result = request(
            "POST",
            "/auth/v1/token?grant_type=refresh_token",
            payload = JSONObject().put("refresh_token", session.refreshToken)
        ) as? JSONObject ?: throw IllegalStateException("پاسخ تمدید نشست معتبر نیست.")
        parseSession(result) ?: throw IllegalStateException("تمدید نشست انجام نشد.")
    }

    suspend fun rooms(session: NativeSession?): List<NativeRoom> = withContext(Dispatchers.IO) {
        val path = "/rest/v1/tournament_rooms?select=id,title,game_category,price_category,game_mode,map,status,starts_at,capacity,team_size,entry_demo_points,prize_demo_points&status=in.(open,full,completed)&order=starts_at.asc&limit=100"
        val rows = request("GET", path, session) as? JSONArray ?: JSONArray()
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            fun optional(name: String) = row.optString(name)
                .takeIf { it.isNotBlank() && it != "null" }
            NativeRoom(
                id = row.optString("id"),
                title = row.optString("title", "روم بدون عنوان"),
                gameCategory = optional("game_category") ?: "uncategorized",
                priceCategory = optional("price_category") ?: "uncategorized",
                gameMode = optional("game_mode"),
                map = optional("map"),
                status = row.optString("status"),
                startsAt = optional("starts_at"),
                capacity = row.optInt("capacity"),
                teamSize = row.optInt("team_size", 4),
                entryPoints = row.optLong("entry_demo_points"),
                firstPrize = row.optLong("prize_demo_points")
            )
        }
    }

    suspend fun adminPermissions(session: NativeSession): List<String> = withContext(Dispatchers.IO) {
        val rows = request("POST", "/rest/v1/rpc/admin_my_permissions", session, JSONObject()) as? JSONArray
            ?: return@withContext emptyList()
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            if (row.optBoolean("is_allowed")) row.optString("permission_code").takeIf { it.isNotBlank() } else null
        }
    }

    suspend fun signOut(session: NativeSession) = withContext(Dispatchers.IO) {
        request("POST", "/auth/v1/logout", session, JSONObject())
    }

    fun loadSession(context: Context) = NativeSessionVault.load(context)
    fun saveSession(context: Context, session: NativeSession) = NativeSessionVault.save(context, session)
    fun clearSession(context: Context) = NativeSessionVault.clear(context)
}
