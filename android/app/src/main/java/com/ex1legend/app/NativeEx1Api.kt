package com.ex1legend.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
    val endsAt: String?,
    val capacity: Int,
    val teamSize: Int,
    val maxTeams: Int,
    val entryPoints: Long,
    val firstPrize: Long
)

data class NativeAdminSnapshot(
    val tournaments: List<JSONObject>,
    val registrations: List<JSONObject>,
    val teams: List<JSONObject>,
    val users: List<JSONObject>,
    val wallet: List<JSONObject>
)

data class NativePlayerResult(
    val playerName: String,
    val placement: Int?,
    val kills: Int,
    val points: Int
)

data class NativeSupportMessage(
    val senderRole: String,
    val body: String,
    val createdAt: String
)

data class NativeSupportConversation(
    val id: String,
    val status: String,
    val updatedAt: String
)

data class NativeNotification(
    val id: String,
    val title: String,
    val body: String,
    val type: String,
    val readAt: String?,
    val createdAt: String
)

data class NativeTeamMember(
    val userId: String,
    val displayName: String,
    val username: String?,
    val role: String,
    val joinedAt: String?
)

data class NativeTeam(
    val id: String,
    val name: String,
    val tag: String?,
    val description: String?,
    val captainId: String,
    val logoUrl: String?,
    val members: List<NativeTeamMember>,
    val isCaptain: Boolean
)

data class NativeTeamInvite(
    val id: String,
    val teamId: String,
    val teamName: String,
    val teamTag: String?,
    val inviterName: String,
    val inviterUsername: String?,
    val createdAt: String
)

data class NativeSentTeamInvite(
    val id: String,
    val displayName: String,
    val username: String?,
    val createdAt: String
)

data class NativeRoomSlot(
    val number: Int,
    val teamName: String,
    val status: String,
    val players: List<String>
)

data class NativeSoloStatus(
    val registered: Boolean,
    val status: String?,
    val slotNumber: Int?,
    val waiting: Int?,
    val needed: Int?
)

data class NativeRoomAccess(
    val accessReady: Boolean,
    val waitingForStart: Boolean,
    val waitingForAdmin: Boolean,
    val roomCode: String?,
    val roomPassword: String?,
    val slotNumber: Int?,
    val teamName: String?,
    val startsAt: String?,
    val rules: String?
)

data class NativeProfile(
    val id: String,
    val displayName: String?,
    val username: String?,
    val gameUid: String?,
    val gameName: String?,
    val avatarUrl: String?,
    val bio: String?
)

data class NativePublicProfile(
    val id: String,
    val displayName: String?,
    val username: String?,
    val gameName: String?,
    val avatarUrl: String?,
    val coverUrl: String?,
    val bio: String?,
    val roleBadge: String?
)

data class NativeRankedPlayer(
    val userId: String?,
    val playerName: String,
    val username: String?,
    val gameName: String?,
    val tournamentTitle: String?,
    val roomTitle: String?,
    val placement: Int?,
    val kills: Int,
    val points: Int
)

data class NativeWalletEntry(
    val type: String,
    val amount: Long,
    val balanceAfter: Long,
    val reason: String,
    val createdAt: String
)

data class NativeWallet(
    val balance: Long,
    val history: List<NativeWalletEntry>
)

class NativeApiException(val status: Int, message: String) : Exception(message)

private fun isBioColumnUnavailable(error: NativeApiException): Boolean {
    val message = error.message.orEmpty().lowercase()
    return error.status == 400 && message.contains("bio") &&
        (message.contains("column") || message.contains("schema cache") || message.contains("field"))
}

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

    suspend fun signUp(email: String, password: String, displayName: String): NativeSession? = withContext(Dispatchers.IO) {
        val result = request(
            "POST",
            "/auth/v1/signup",
            payload = JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .put("data", JSONObject().put("display_name", displayName.trim()))
        ) as? JSONObject ?: throw IllegalStateException("پاسخ ثبت‌نام معتبر نیست.")
        parseSession(result)
    }

    suspend fun requestPasswordReset(email: String) = withContext(Dispatchers.IO) {
        val redirect = java.net.URLEncoder.encode(BuildConfig.EX1_BASE_URL.trimEnd('/') + "/app.html", "UTF-8")
        request(
            "POST",
            "/auth/v1/recover?redirect_to=$redirect",
            payload = JSONObject().put("email", email.trim())
        )
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
        val path = "/rest/v1/tournament_rooms?select=id,title,game_category,price_category,game_mode,map,status,starts_at,ends_at,capacity,team_size,max_teams,entry_demo_points,prize_demo_points&status=in.(open,full,completed)&order=starts_at.asc&limit=100"
        val rows = try {
            request("GET", path, session) as? JSONArray ?: JSONArray()
        } catch (e: NativeApiException) {
            if (!isBioColumnUnavailable(e)) throw e
            val legacyBase = "/rest/v1/profiles?select=id,display_name,username,game_name,avatar_url,cover_url&id=neq.${session.userId}&order=created_at.desc&limit=50"
            val legacyPath = if (clean.isBlank()) legacyBase else legacyBase + "&or=" +
                java.net.URLEncoder.encode("(display_name.ilike.*$clean*,username.ilike.*$clean*)", "UTF-8")
            request("GET", legacyPath, session) as? JSONArray ?: JSONArray()
        }
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            fun optional(name: String) = row.optString(name)
                .takeIf { it.isNotBlank() && it != "null" }
            NativeRoom(
                id = row.optString("id"),
                title = row.optString("title", "روم بدون عنوان"),
                gameCategory = normalizeGameCategory(optional("game_category")),
                priceCategory = normalizePriceCategory(optional("price_category")),
                gameMode = optional("game_mode"),
                map = optional("map"),
                status = row.optString("status"),
                startsAt = optional("starts_at"),
                endsAt = optional("ends_at"),
                capacity = row.optInt("capacity"),
                teamSize = row.optInt("team_size", 4),
                maxTeams = row.optInt("max_teams"),
                entryPoints = row.optLong("entry_demo_points"),
                firstPrize = row.optLong("prize_demo_points")
            )
        }
    }

    suspend fun latestSupportConversation(session: NativeSession): NativeSupportConversation? = withContext(Dispatchers.IO) {
        val rows = request(
            "GET",
            "/rest/v1/support_conversations?select=id,status,updated_at&user_id=eq.${session.userId}&order=updated_at.desc&limit=1",
            session
        ) as? JSONArray ?: JSONArray()
        if (rows.length() == 0) return@withContext null
        val row = rows.getJSONObject(0)
        NativeSupportConversation(row.optString("id"), row.optString("status"), row.optString("updated_at"))
    }

    suspend fun supportMessages(session: NativeSession, conversationId: String): List<NativeSupportMessage> = withContext(Dispatchers.IO) {
        val rows = request(
            "GET",
            "/rest/v1/support_messages?select=sender_role,body,created_at&conversation_id=eq.$conversationId&order=created_at.asc&limit=100",
            session
        ) as? JSONArray ?: JSONArray()
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            NativeSupportMessage(row.optString("sender_role"), row.optString("body"), row.optString("created_at"))
        }
    }

    suspend fun sendSupportMessage(
        session: NativeSession,
        message: String,
        conversationId: String?
    ): JSONObject = withContext(Dispatchers.IO) {
        request(
            "POST",
            "/functions/v1/ai-support",
            session,
            JSONObject()
                .put("message", message.trim())
                .put("conversation_id", conversationId ?: JSONObject.NULL)
        ) as? JSONObject ?: throw IllegalStateException("پاسخ پشتیبانی معتبر نیست.")
    }

    suspend fun notifications(session: NativeSession): List<NativeNotification> = withContext(Dispatchers.IO) {
        val rows = request(
            "GET",
            "/rest/v1/user_notifications?select=id,title,body,notification_type,read_at,created_at&user_id=eq.${session.userId}&order=created_at.desc&limit=20",
            session
        ) as? JSONArray ?: JSONArray()
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            NativeNotification(
                row.optString("id"),
                row.optString("title"),
                row.optString("body"),
                row.optString("notification_type"),
                row.optString("read_at").takeIf { it.isNotBlank() && it != "null" },
                row.optString("created_at")
            )
        }
    }

    suspend fun markNotificationRead(session: NativeSession, notificationId: String, readAt: String) = withContext(Dispatchers.IO) {
        request(
            "PATCH",
            "/rest/v1/user_notifications?id=eq.$notificationId&user_id=eq.${session.userId}",
            session,
            JSONObject().put("read_at", readAt)
        )
    }

    suspend fun deleteNotification(session: NativeSession, notificationId: String) = withContext(Dispatchers.IO) {
        request(
            "DELETE",
            "/rest/v1/user_notifications?id=eq.$notificationId&user_id=eq.${session.userId}",
            session
        )
    }

    suspend fun myTeam(session: NativeSession): NativeTeam? = withContext(Dispatchers.IO) {
        val memberships = request(
            "GET",
            "/rest/v1/team_members?select=team_id,role&user_id=eq.${session.userId}&limit=1",
            session
        ) as? JSONArray ?: JSONArray()
        if (memberships.length() == 0) return@withContext null
        val teamId = memberships.getJSONObject(0).optString("team_id")
        if (teamId.isBlank()) return@withContext null
        val teamRows = request(
            "GET",
            "/rest/v1/teams?select=id,name,tag,description,captain_id,logo_url&id=eq.$teamId&limit=1",
            session
        ) as? JSONArray ?: JSONArray()
        if (teamRows.length() == 0) return@withContext null
        val team = teamRows.getJSONObject(0)
        val memberRows = request(
            "GET",
            "/rest/v1/team_members?select=user_id,role,joined_at&team_id=eq.$teamId&order=joined_at.asc",
            session
        ) as? JSONArray ?: JSONArray()
        val ids = (0 until memberRows.length()).mapNotNull { memberRows.optJSONObject(it)?.optString("user_id") }
            .filter { it.isNotBlank() }
        val profiles = if (ids.isEmpty()) JSONArray() else {
            request(
                "GET",
                "/rest/v1/profiles?select=id,display_name,username&id=in.(${ids.joinToString(",")})",
                session
            ) as? JSONArray ?: JSONArray()
        }
        val profilesById = (0 until profiles.length()).mapNotNull { profiles.optJSONObject(it) }
            .associateBy { it.optString("id") }
        val members = (0 until memberRows.length()).mapNotNull { index ->
            val row = memberRows.optJSONObject(index) ?: return@mapNotNull null
            val userId = row.optString("user_id")
            val person = profilesById[userId] ?: JSONObject()
            NativeTeamMember(
                userId,
                person.optString("display_name").ifBlank { "بازیکن" },
                person.optString("username").takeIf { it.isNotBlank() && it != "null" },
                row.optString("role"),
                row.optString("joined_at").takeIf { it.isNotBlank() && it != "null" }
            )
        }
        NativeTeam(
            team.optString("id"),
            team.optString("name"),
            team.optString("tag").takeIf { it.isNotBlank() && it != "null" },
            team.optString("description").takeIf { it.isNotBlank() && it != "null" },
            team.optString("captain_id"),
            team.optString("logo_url").takeIf { it.isNotBlank() && it != "null" },
            members,
            team.optString("captain_id") == session.userId
        )
    }

    suspend fun incomingTeamInvites(session: NativeSession): List<NativeTeamInvite> = withContext(Dispatchers.IO) {
        val rows = request(
            "GET",
            "/rest/v1/team_invites?select=id,team_id,created_at,teams(name,tag),profiles!team_invites_invited_by_fkey(display_name,username)&invited_user_id=eq.${session.userId}&status=eq.pending&order=created_at.desc",
            session
        ) as? JSONArray ?: JSONArray()
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val team = row.optJSONObject("teams") ?: JSONObject()
            val inviter = row.optJSONObject("profiles") ?: JSONObject()
            NativeTeamInvite(
                row.optString("id"),
                row.optString("team_id"),
                team.optString("name", "تیم EX1"),
                team.optString("tag").takeIf { it.isNotBlank() && it != "null" },
                inviter.optString("display_name").ifBlank { inviter.optString("username", "کاپیتان") },
                inviter.optString("username").takeIf { it.isNotBlank() && it != "null" },
                row.optString("created_at")
            )
        }
    }

    suspend fun sentTeamInvites(session: NativeSession, teamId: String): List<NativeSentTeamInvite> = withContext(Dispatchers.IO) {
        val rows = request(
            "GET",
            "/rest/v1/team_invites?select=id,created_at,profiles!team_invites_invited_user_id_fkey(display_name,username)&team_id=eq.$teamId&status=eq.pending&order=created_at.desc",
            session
        ) as? JSONArray ?: JSONArray()
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val person = row.optJSONObject("profiles") ?: JSONObject()
            NativeSentTeamInvite(
                row.optString("id"),
                person.optString("display_name").ifBlank { "بازیکن" },
                person.optString("username").takeIf { it.isNotBlank() && it != "null" },
                row.optString("created_at")
            )
        }
    }

    suspend fun createTeam(session: NativeSession, name: String, tag: String, description: String): JSONObject = withContext(Dispatchers.IO) {
        request(
            "POST",
            "/functions/v1/manage-team",
            session,
            JSONObject().put("action", "create").put("name", name.trim())
                .put("tag", if (tag.isBlank()) JSONObject.NULL else tag.trim().uppercase())
                .put("description", if (description.isBlank()) JSONObject.NULL else description.trim())
        ) as? JSONObject ?: throw IllegalStateException("پاسخ ساخت تیم معتبر نیست.")
    }

    suspend fun updateTeam(session: NativeSession, teamId: String, name: String, tag: String, description: String): JSONObject = withContext(Dispatchers.IO) {
        request(
            "POST",
            "/functions/v1/manage-team",
            session,
            JSONObject().put("action", "update").put("team_id", teamId)
                .put("name", name.trim())
                .put("tag", if (tag.isBlank()) JSONObject.NULL else tag.trim().uppercase())
                .put("description", if (description.isBlank()) JSONObject.NULL else description.trim())
        ) as? JSONObject ?: throw IllegalStateException("پاسخ ویرایش تیم معتبر نیست.")
    }

    suspend fun createTeamInvite(session: NativeSession, username: String) = withContext(Dispatchers.IO) {
        request("POST", "/rest/v1/rpc/team_invite_create", session, JSONObject().put("p_username", username.trim()))
    }

    suspend fun respondTeamInvite(session: NativeSession, inviteId: String, accept: Boolean) = withContext(Dispatchers.IO) {
        request(
            "POST",
            "/rest/v1/rpc/team_invite_respond",
            session,
            JSONObject().put("p_invite_id", inviteId).put("p_accept", accept)
        )
    }

    suspend fun cancelTeamInvite(session: NativeSession, inviteId: String) = withContext(Dispatchers.IO) {
        request("POST", "/rest/v1/rpc/team_invite_cancel", session, JSONObject().put("p_invite_id", inviteId))
    }

    suspend fun removeTeamMember(session: NativeSession, userId: String) = withContext(Dispatchers.IO) {
        request("POST", "/rest/v1/rpc/team_member_remove", session, JSONObject().put("p_user_id", userId))
    }

    suspend fun leaveTeam(session: NativeSession) = withContext(Dispatchers.IO) {
        request("POST", "/rest/v1/rpc/team_leave", session, JSONObject())
    }

    suspend fun deleteTeam(session: NativeSession) = withContext(Dispatchers.IO) {
        request("POST", "/rest/v1/rpc/team_delete", session, JSONObject())
    }

    suspend fun roomResults(session: NativeSession, roomId: String): List<NativePlayerResult> = withContext(Dispatchers.IO) {
        val rows = request(
            "GET",
            "/rest/v1/tournament_player_results?select=placement,kills,points,profiles(display_name,username,game_name)&room_id=eq.$roomId&order=placement.asc.nullslast,points.desc,kills.desc",
            session
        ) as? JSONArray ?: JSONArray()
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val person = row.optJSONObject("profiles") ?: JSONObject()
            val name = person.optString("game_name").takeIf { it.isNotBlank() && it != "null" }
                ?: person.optString("display_name").takeIf { it.isNotBlank() && it != "null" }
                ?: person.optString("username").takeIf { it.isNotBlank() && it != "null" }
                ?: "بازیکن"
            NativePlayerResult(
                name,
                row.optInt("placement").takeIf { !row.isNull("placement") },
                row.optInt("kills"),
                row.optInt("points")
            )
        }
    }

    suspend fun roomSlots(session: NativeSession, roomId: String): List<NativeRoomSlot> = withContext(Dispatchers.IO) {
        val rows = request(
            "POST",
            "/rest/v1/rpc/get_tournament_room_slots",
            session,
            JSONObject().put("p_room_id", roomId)
        ) as? JSONArray ?: JSONArray()
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val playerRows = row.optJSONArray("players") ?: JSONArray()
            val players = (0 until playerRows.length()).mapNotNull { playerIndex ->
                val player = playerRows.optJSONObject(playerIndex) ?: return@mapNotNull null
                player.optString("game_name").takeIf { it.isNotBlank() && it != "null" }
                    ?: player.optString("display_name").takeIf { it.isNotBlank() && it != "null" }
            }
            NativeRoomSlot(
                row.optInt("slot_number"),
                row.optString("team_name"),
                row.optString("status"),
                players
            )
        }
    }

    suspend fun soloStatus(session: NativeSession, roomId: String): NativeSoloStatus = withContext(Dispatchers.IO) {
        val row = request(
            "POST",
            "/rest/v1/rpc/get_my_room_solo_status",
            session,
            JSONObject().put("p_room_id", roomId)
        ) as? JSONObject ?: JSONObject()
        NativeSoloStatus(
            row.optBoolean("registered"),
            row.optString("status").takeIf { it.isNotBlank() && it != "null" },
            row.optInt("slot_number").takeIf { row.has("slot_number") && !row.isNull("slot_number") },
            row.optInt("players_waiting").takeIf { row.has("players_waiting") && !row.isNull("players_waiting") },
            row.optInt("players_needed").takeIf { row.has("players_needed") && !row.isNull("players_needed") }
        )
    }

    suspend fun roomAccess(session: NativeSession, roomId: String): NativeRoomAccess = withContext(Dispatchers.IO) {
        val row = request(
            "POST",
            "/rest/v1/rpc/get_my_tournament_room_access",
            session,
            JSONObject().put("p_room_id", roomId)
        ) as? JSONObject ?: JSONObject()
        fun optional(name: String) = row.optString(name).takeIf { it.isNotBlank() && it != "null" }
        NativeRoomAccess(
            row.optBoolean("access_ready"),
            row.optBoolean("waiting_for_start"),
            row.optBoolean("waiting_for_admin"),
            optional("room_code"),
            optional("room_password"),
            row.optInt("slot_number").takeIf { row.has("slot_number") && !row.isNull("slot_number") },
            optional("team_name"),
            optional("starts_at"),
            optional("rules")
        )
    }

    suspend fun registerForRoom(session: NativeSession, roomId: String, slotNumber: Int): JSONObject = withContext(Dispatchers.IO) {
        request(
            "POST",
            "/rest/v1/rpc/register_for_tournament_room",
            session,
            JSONObject().put("p_room_id", roomId).put("p_slot_number", slotNumber)
        ) as? JSONObject ?: throw IllegalStateException("پاسخ ثبت‌نام معتبر نیست.")
    }

    suspend fun registerSolo(session: NativeSession, roomId: String, slotNumber: Int): JSONObject = withContext(Dispatchers.IO) {
        request(
            "POST",
            "/rest/v1/rpc/register_for_tournament_room_solo",
            session,
            JSONObject().put("p_room_id", roomId).put("p_slot_number", slotNumber)
        ) as? JSONObject ?: throw IllegalStateException("پاسخ ثبت‌نام انفرادی معتبر نیست.")
    }

    suspend fun profile(session: NativeSession): NativeProfile? = withContext(Dispatchers.IO) {
        val rows = try {
            request(
                "GET",
                "/rest/v1/profiles?select=id,display_name,username,game_uid,game_name,avatar_url,bio&id=eq.${session.userId}&limit=1",
                session
            ) as? JSONArray ?: JSONArray()
        } catch (e: NativeApiException) {
            if (!isBioColumnUnavailable(e)) throw e
            request(
                "GET",
                "/rest/v1/profiles?select=id,display_name,username,game_uid,game_name,avatar_url&id=eq.${session.userId}&limit=1",
                session
            ) as? JSONArray ?: JSONArray()
        }
        if (rows.length() == 0) return@withContext null
        val row = rows.getJSONObject(0)
        fun optional(name: String) = row.optString(name).takeIf { it.isNotBlank() && it != "null" }
        NativeProfile(
            row.optString("id"),
            optional("display_name"),
            optional("username"),
            optional("game_uid"),
            optional("game_name"),
            optional("avatar_url"),
            optional("bio")
        )
    }

    suspend fun explorePlayers(session: NativeSession, query: String): List<NativePublicProfile> = withContext(Dispatchers.IO) {
        val clean = query.trim().take(40).filter { it.isLetterOrDigit() || it.isWhitespace() || it in "._-" }.replace(Regex("\\s+"), " ")
        val base = "/rest/v1/profiles?select=id,display_name,username,game_name,avatar_url,cover_url,bio&id=neq.${session.userId}&order=created_at.desc&limit=50"
        val path = if (clean.isBlank()) base else base + "&or=" +
            java.net.URLEncoder.encode("(display_name.ilike.*$clean*,username.ilike.*$clean*)", "UTF-8")
        val rows = request("GET", path, session) as? JSONArray ?: JSONArray()
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            fun optional(name: String) = row.optString(name).takeIf { it.isNotBlank() && it != "null" }
            NativePublicProfile(
                row.optString("id"),
                optional("display_name"),
                optional("username"),
                optional("game_name"),
                optional("avatar_url"),
                optional("cover_url"),
                optional("bio"),
                null
            )
        }
    }

    suspend fun publicProfile(session: NativeSession, userId: String): NativePublicProfile? = withContext(Dispatchers.IO) {
        if (!userId.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))) throw IllegalArgumentException("شناسه بازیکن معتبر نیست.")
        val rows = try {
            request(
                "GET",
                "/rest/v1/profiles?select=id,display_name,username,game_name,avatar_url,cover_url,bio&id=eq.$userId&limit=1",
                session
            ) as? JSONArray ?: JSONArray()
        } catch (e: NativeApiException) {
            if (!isBioColumnUnavailable(e)) throw e
            request(
                "GET",
                "/rest/v1/profiles?select=id,display_name,username,game_name,avatar_url,cover_url&id=eq.$userId&limit=1",
                session
            ) as? JSONArray ?: JSONArray()
        }
        if (rows.length() == 0) return@withContext null
        val row = rows.getJSONObject(0)
        fun optional(name: String) = row.optString(name).takeIf { it.isNotBlank() && it != "null" }
        val badges = runCatching {
            request(
                "POST",
                "/rest/v1/rpc/ex1_public_role_badges",
                session,
                JSONObject().put("p_user_ids", JSONArray().put(userId))
            ) as? JSONArray
        }.getOrNull() ?: JSONArray()
        val badge = (0 until badges.length()).mapNotNull { badges.optJSONObject(it) }
            .firstOrNull { it.optString("user_id") == userId }?.optString("badge_type")?.takeIf { it.isNotBlank() }
        NativePublicProfile(
            row.optString("id"),
            optional("display_name"),
            optional("username"),
            optional("game_name"),
            optional("avatar_url"),
            optional("cover_url"),
            optional("bio"),
            badge
        )
    }

    suspend fun rankingPlayers(session: NativeSession): List<NativeRankedPlayer> = withContext(Dispatchers.IO) {
        val rows = request(
            "GET",
            "/rest/v1/tournament_player_results?select=user_id,placement,kills,points,profiles(id,display_name,username,game_name),tournaments(title),tournament_rooms(title)&order=points.desc,kills.desc&limit=30",
            session
        ) as? JSONArray ?: JSONArray()
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val person = row.optJSONObject("profiles") ?: JSONObject()
            val tournament = row.optJSONObject("tournaments") ?: JSONObject()
            val room = row.optJSONObject("tournament_rooms") ?: JSONObject()
            val gameName = person.optString("game_name").takeIf { it.isNotBlank() && it != "null" }
            val displayName = person.optString("display_name").takeIf { it.isNotBlank() && it != "null" }
            val username = person.optString("username").takeIf { it.isNotBlank() && it != "null" }
            NativeRankedPlayer(
                row.optString("user_id").takeIf { it.isNotBlank() && it != "null" },
                gameName ?: displayName ?: username ?: "بازیکن",
                username,
                gameName,
                tournament.optString("title").takeIf { it.isNotBlank() && it != "null" },
                room.optString("title").takeIf { it.isNotBlank() && it != "null" },
                row.optInt("placement").takeIf { !row.isNull("placement") },
                row.optInt("kills"),
                row.optInt("points")
            )
        }
    }

    suspend fun updateProfile(
        session: NativeSession,
        displayName: String,
        username: String,
        gameUid: String,
        gameName: String,
        bio: String
    ): JSONObject = withContext(Dispatchers.IO) {
        request(
            "POST",
            "/functions/v1/update-profile",
            session,
            JSONObject()
                .put("display_name", displayName.trim())
                .put("username", username.trim().ifBlank { JSONObject.NULL })
                .put("game_uid", gameUid.trim().ifBlank { JSONObject.NULL })
                .put("game_name", gameName.trim().ifBlank { JSONObject.NULL })
                .put("bio", bio.trim().ifBlank { JSONObject.NULL })
        ) as? JSONObject ?: throw IllegalStateException("پاسخ ذخیره پروفایل معتبر نیست.")
    }

    suspend fun deleteAccount(
        session: NativeSession,
        password: String,
        confirmation: String
    ): JSONObject = withContext(Dispatchers.IO) {
        request(
            "POST",
            "/functions/v1/delete-account",
            session,
            JSONObject().put("password", password).put("confirmation", confirmation)
        ) as? JSONObject ?: throw IllegalStateException("پاسخ حذف حساب معتبر نیست.")
    }

    suspend fun wallet(session: NativeSession): NativeWallet = withContext(Dispatchers.IO) {
        val summary = request("POST", "/rest/v1/rpc/wallet_my_summary", session, JSONObject())
        val first = when (summary) {
            is JSONArray -> if (summary.length() > 0) summary.optJSONObject(0) else null
            is JSONObject -> summary
            else -> null
        }
        val history = request(
            "POST",
            "/rest/v1/rpc/wallet_my_history",
            session,
            JSONObject().put("p_limit", 30)
        ) as? JSONArray ?: JSONArray()
        val entries = (0 until history.length()).mapNotNull { index ->
            val row = history.optJSONObject(index) ?: return@mapNotNull null
            NativeWalletEntry(
                row.optString("entry_type"),
                row.optLong("amount_units"),
                row.optLong("balance_after_units"),
                row.optString("reason"),
                row.optString("created_at")
            )
        }
        NativeWallet(first?.optLong("balance_units") ?: 0L, entries)
    }

    private suspend fun adminRows(
        session: NativeSession,
        rpcName: String,
        args: JSONObject = JSONObject()
    ): List<JSONObject> = withContext(Dispatchers.IO) {
        val rows = request("POST", "/rest/v1/rpc/$rpcName", session, args) as? JSONArray ?: JSONArray()
        (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
    }

    suspend fun adminSnapshot(session: NativeSession, permissions: List<String>): NativeAdminSnapshot = kotlinx.coroutines.coroutineScope {
        val canTournaments = permissions.contains("tournaments.view")
        val canRegistrations = permissions.contains("registrations.view_all")
        val canTeams = permissions.contains("teams.view")
        val canUsers = permissions.contains("users.view") || permissions.contains("profiles.view_all")
        val canWallet = permissions.contains("wallet.view_all")
        val tournaments = async { if (canTournaments) adminRows(session, "admin_dashboard_tournaments") else emptyList() }
        val registrations = async { if (canRegistrations) adminRows(session, "admin_dashboard_registrations") else emptyList() }
        val teams = async { if (canTeams) adminRows(session, "admin_dashboard_teams") else emptyList() }
        val users = async { if (canUsers) adminRows(session, "admin_dashboard_users") else emptyList() }
        val wallet = async {
            if (canWallet) adminRows(session, "admin_wallet_demo_overview_v2", JSONObject().put("p_limit", 200))
            else emptyList()
        }
        NativeAdminSnapshot(tournaments.await(), registrations.await(), teams.await(), users.await(), wallet.await())
    }

    suspend fun isCurrentUserBanned(session: NativeSession): Boolean = withContext(Dispatchers.IO) {
        request("POST", "/rest/v1/rpc/is_current_user_banned", session, JSONObject()) == true
    }

    suspend fun isCurrentUserOwner(session: NativeSession): Boolean = withContext(Dispatchers.IO) {
        request("POST", "/rest/v1/rpc/is_current_user_owner", session, JSONObject()) == true
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
