package com.ali.textchat.data

import android.util.Log
import com.ali.textchat.model.ChatMessage
import com.ali.textchat.model.ChatRoom
import com.ali.textchat.model.ChatUser
import com.ali.textchat.model.PmThread
import com.ali.textchat.model.UserRank
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Full client for server.js: auth (register/login with token), room join,
 * public + private messages, presence, moderation and profile. Published as
 * StateFlows the Compose screens observe.
 */
class ChatSocket(
    private val serverUrl: String = AppConfig.SERVER_URL,
    private val deviceHash: String
) {
    enum class Status { DISCONNECTED, CONNECTING, CONNECTED, BANNED }

    private var socket: Socket? = null

    private val _status = MutableStateFlow(Status.DISCONNECTED)
    val status: StateFlow<Status> = _status
    private val _errors = MutableStateFlow<String?>(null)
    val errors: StateFlow<String?> = _errors
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages
    private val _users = MutableStateFlow<List<ChatUser>>(emptyList())
    val users: StateFlow<List<ChatUser>> = _users
    private val _rooms = MutableStateFlow<List<ChatRoom>>(emptyList())
    val rooms: StateFlow<List<ChatRoom>> = _rooms
    private val _me = MutableStateFlow<ChatUser?>(null)
    val me: StateFlow<ChatUser?> = _me
    private val _room = MutableStateFlow<ChatRoom?>(null)
    val room: StateFlow<ChatRoom?> = _room
    private val _privates = MutableStateFlow<List<ChatMessage>>(emptyList())
    val privates: StateFlow<List<ChatMessage>> = _privates
    private val _youtubeId = MutableStateFlow<String?>(null)
    val youtubeId: StateFlow<String?> = _youtubeId
    private val _youtubeTitle = MutableStateFlow<String?>(null)
    val youtubeTitle: StateFlow<String?> = _youtubeTitle
    private val _youtubeBy = MutableStateFlow<String?>(null)
    val youtubeBy: StateFlow<String?> = _youtubeBy
    private val _youtubeOffset = MutableStateFlow<Int>(0)
    val youtubeOffset: StateFlow<Int> = _youtubeOffset
    private val _youtubeIsWelcome = MutableStateFlow<Boolean>(false)
    val youtubeIsWelcome: StateFlow<Boolean> = _youtubeIsWelcome
    private val _notifications = MutableStateFlow<List<String>>(emptyList())
    val notifications: StateFlow<List<String>> = _notifications
    private val _threads = MutableStateFlow<List<PmThread>>(emptyList())
    val threads: StateFlow<List<PmThread>> = _threads
    private val _requests = MutableStateFlow<List<PmThread>>(emptyList())
    val requests: StateFlow<List<PmThread>> = _requests
    private val _scores = MutableStateFlow<List<PmThread>>(emptyList())
    val scores: StateFlow<List<PmThread>> = _scores

    private fun notify(line: String) { _notifications.value = (_notifications.value + line).takeLast(50) }

    var token: String? = null
        private set

    private fun ensureSocket(onConnected: () -> Unit) {
        if (socket?.connected() == true) { onConnected(); return }
        try {
            val opts = IO.Options().apply {
                query = "deviceHash=" + URLEncoder.encode(deviceHash, "UTF-8")
                reconnection = true
                forceNew = true
            }
            _status.value = Status.CONNECTING
            val s = IO.socket(serverUrl, opts)
            socket = s
            s.on(Socket.EVENT_CONNECT) { _status.value = Status.CONNECTED; onConnected() }
            s.on(Socket.EVENT_DISCONNECT) { if (_status.value != Status.BANNED) _status.value = Status.DISCONNECTED }
            s.on(Socket.EVENT_CONNECT_ERROR) { args ->
                val m = args.firstOrNull()?.toString() ?: ""
                if (m.contains("DEVICE_BANNED")) { _status.value = Status.BANNED; _errors.value = "تم حظر عتاد هذا الجهاز نهائياً" }
                else _status.value = Status.DISCONNECTED
            }
            s.on("new_message") { a -> (a.firstOrNull() as? JSONObject)?.let { _messages.value = _messages.value + parseMessage(it) } }
            s.on("system_message") { a -> (a.firstOrNull() as? JSONObject)?.let { o ->
                _messages.value = _messages.value + ChatMessage(
                    id = "sys${System.nanoTime()}", roomId = o.optString("roomId"),
                    senderId = "system", senderName = "النظام", senderAvatar = "",
                    senderRank = UserRank.BOT, text = o.optString("text"), timestamp = ""
                )
            } }
            s.on("user_list") { a -> (a.firstOrNull() as? JSONObject)?.optJSONArray("users")?.let { _users.value = parseUsers(it) } }
            s.on("user_updated") { a -> (a.firstOrNull() as? JSONObject)?.optJSONObject("user")?.let { upd ->
                val u = parseUser(upd)
                _users.value = _users.value.map { if (it.id == u.id) u else it }
                if (_me.value?.id == u.id) _me.value = u
            } }
            s.on("rooms") { a -> (a.firstOrNull() as? JSONObject)?.optJSONArray("rooms")?.let { _rooms.value = parseRooms(it) } }
            s.on("private_message") { a -> (a.firstOrNull() as? JSONObject)?.let {
                val m = parseMessage(it)
                _privates.value = _privates.value + m
                if (m.senderId != _me.value?.id) notify("✉️ خاص من ${m.senderName}: ${m.text.take(40)}")
            } }
            s.on("request_received") { a -> (a.firstOrNull() as? JSONObject)?.let {
                notify("👋 ${it.optString("fromName")} يريد إضافتك")
            } }
            s.on("error_alert") { a -> _errors.value = (a.firstOrNull() as? JSONObject)?.optString("message") }
            s.on("broadcast") { a -> (a.firstOrNull() as? JSONObject)?.let {
                _errors.value = "📢 ${it.optString("by")}: ${it.optString("text")}"
                notify("📢 ${it.optString("by")}: ${it.optString("text")}")
            } }
            s.on("youtube_updated") { a ->
                (a.firstOrNull() as? JSONObject)?.let { obj ->
                    val vId = obj.optString("videoId")
                    _youtubeId.value = if (vId.isNotBlank()) vId else null
                    val vTitle = obj.optString("videoTitle")
                    _youtubeTitle.value = if (vTitle.isNotBlank()) vTitle else null
                    val vBy = obj.optString("startedBy").ifBlank { obj.optString("by") }
                    _youtubeBy.value = if (vBy.isNotBlank() && vBy != "system") vBy else null
                    _youtubeOffset.value = obj.optInt("offset", 0)
                    _youtubeIsWelcome.value = obj.optBoolean("isWelcome", false) || (vBy == "فيديو ترحيبي")
                }
            }
            s.on("force_disconnect") { a ->
                _status.value = Status.BANNED
                _errors.value = (a.firstOrNull() as? JSONObject)?.optString("reason") ?: "تم فصلك من قبل الإدارة"
            }
            s.connect()
        } catch (e: Exception) {
            Log.e("ChatSocket", "connect failed: ${e.message}")
            _status.value = Status.DISCONNECTED
        }
    }

    fun register(
        name: String,
        password: String,
        guest: Boolean = false,
        age: Int? = null,
        gender: String? = null,
        country: String? = null,
        bio: String? = null,
        isGhost: Boolean = false,
        lockPrivate: Boolean = false,
        muteNotifications: Boolean = false,
        onResult: (Boolean, String?) -> Unit
    ) {
        ensureSocket {
            val payload = JSONObject().apply {
                put("name", name)
                put("password", password)
                if (guest) put("guest", true)
                if (age != null) put("age", age)
                if (!gender.isNullOrBlank()) put("gender", gender)
                if (!country.isNullOrBlank()) put("country", country)
                if (!bio.isNullOrBlank()) put("bio", bio)
                put("isGhost", isGhost)
                put("lockPrivate", lockPrivate)
                put("muteNotifications", muteNotifications)
            }
            socket?.emit("register", payload, Ack { res -> handleAuth(res.firstOrNull(), onResult) })
        }
    }

    fun login(
        name: String?,
        password: String?,
        savedToken: String?,
        isGhost: Boolean = false,
        lockPrivate: Boolean = false,
        muteNotifications: Boolean = false,
        onResult: (Boolean, String?) -> Unit
    ) {
        ensureSocket {
            val payload = JSONObject().apply {
                if (name != null) put("name", name)
                if (password != null) put("password", password)
                if (savedToken != null) put("token", savedToken)
                put("isGhost", isGhost)
                put("lockPrivate", lockPrivate)
                put("muteNotifications", muteNotifications)
            }
            socket?.emit("login", payload, Ack { res -> handleAuth(res.firstOrNull(), onResult) })
        }
    }

    fun userInspect(targetUserId: String, onResult: (Boolean, List<String>, List<ChatUser>, String?) -> Unit) {
        ensureSocket {
            val payload = JSONObject().apply { put("targetUserId", targetUserId) }
            socket?.emit("user_inspect", payload, Ack { res ->
                val o = res.firstOrNull() as? JSONObject
                if (o?.optBoolean("ok") == true) {
                    val oldNamesArr = o.optJSONArray("oldNames")
                    val oldNames = (0 until (oldNamesArr?.length() ?: 0)).map { oldNamesArr!!.getString(it) }
                    val linkedArr = o.optJSONArray("linkedAccounts")
                    val linked = (0 until (linkedArr?.length() ?: 0)).map { parseUser(linkedArr!!.getJSONObject(it)) }
                    val ip = o.optString("lastIp")
                    onResult(true, oldNames, linked, ip)
                } else {
                    onResult(false, emptyList(), emptyList(), o?.optString("error"))
                }
            })
        }
    }

    private fun handleAuth(res: Any?, onResult: (Boolean, String?) -> Unit) {
        val o = res as? JSONObject
        if (o?.optBoolean("ok") == true) {
            token = o.optString("token")
            _me.value = parseUser(o.getJSONObject("user"))
            onResult(true, token)
        } else onResult(false, o?.optString("error") ?: "فشل الاتصال")
    }

    fun joinRoom(roomId: String) {
        val t = token ?: return
        socket?.emit("join_room", JSONObject().apply { put("token", t); put("roomId", roomId) }, Ack { res ->
            val o = res.firstOrNull() as? JSONObject ?: return@Ack
            if (!o.optBoolean("ok")) { _errors.value = o.optString("error"); return@Ack }
            o.optJSONObject("room")?.let { r ->
                _room.value = ChatRoom(id = r.optString("id"), title = r.optString("title"), description = "", topic = r.optString("topic"), isLocked = r.optBoolean("lockPublic"))
                val yId = r.optString("youtubeId")
                _youtubeId.value = if (yId.isNotBlank()) yId else null
                val yTitle = r.optString("youtubeTitle")
                _youtubeTitle.value = if (yTitle.isNotBlank()) yTitle else null
                val yBy = r.optString("youtubeStartedBy").ifBlank { r.optString("by") }
                _youtubeBy.value = if (yBy.isNotBlank() && yBy != "system") yBy else null
                _youtubeOffset.value = r.optInt("youtubeOffset", 0)
                _youtubeIsWelcome.value = r.optBoolean("isWelcome", false) || (yBy == "فيديو ترحيبي")
            }
            o.optJSONObject("me")?.let { _me.value = parseUser(it) }
            o.optJSONArray("messages")?.let { _messages.value = parseMessages(it) }
            o.optJSONArray("users")?.let { _users.value = parseUsers(it) }
        })
    }

    fun listRooms() {
        socket?.emit("list_rooms", JSONObject(), Ack { res ->
            (res.firstOrNull() as? JSONObject)?.optJSONArray("rooms")?.let { _rooms.value = parseRooms(it) }
        })
    }

    fun sendMessage(text: String, mediaType: String? = null, mediaUrl: String? = null, audioDuration: Int? = null) {
        socket?.emit("send_message", JSONObject().apply {
            put("text", text)
            if (mediaType != null) put("mediaType", mediaType)
            if (mediaUrl != null) put("mediaUrl", mediaUrl)
            if (audioDuration != null) put("audioDuration", audioDuration)
        })
    }

    fun sendPrivate(toUserId: String, text: String, mediaType: String? = null, mediaUrl: String? = null, audioDuration: Int? = null) {
        socket?.emit("private_send", JSONObject().apply {
            put("toUserId", toUserId)
            put("text", text)
            if (mediaType != null) put("mediaType", mediaType)
            if (mediaUrl != null) put("mediaUrl", mediaUrl)
            if (audioDuration != null) put("audioDuration", audioDuration)
        })
    }

    fun uploadMedia(
        base64Data: String,
        type: String,
        filename: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        Thread {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val jsonBody = JSONObject().apply {
                    put("data", base64Data)
                    put("type", type)
                    put("filename", filename)
                }.toString()

                val mediaTypeJson = "application/json; charset=utf-8".toMediaType()
                val reqBody = jsonBody.toRequestBody(mediaTypeJson)
                val req = okhttp3.Request.Builder()
                    .url("${AppConfig.SERVER_URL}/api/upload")
                    .post(reqBody)
                    .build()

                val response = client.newCall(req).execute()
                val bodyStr = response.body?.string() ?: ""
                val o = JSONObject(bodyStr)
                if (o.optBoolean("ok")) {
                    onResult(true, o.optString("url"))
                } else {
                    onResult(false, o.optString("error", "فشل الرفع"))
                }
            } catch (e: Exception) {
                onResult(false, e.message)
            }
        }.start()
    }
    fun loadPrivateHistory(withUserId: String) {
        socket?.emit("private_history", JSONObject().apply { put("withUserId", withUserId) }, Ack { res ->
            (res.firstOrNull() as? JSONObject)?.optJSONArray("messages")?.let { _privates.value = parseMessages(it) }
        })
    }

    fun updateProfile(
        color: String?,
        avatarUrl: String?,
        statusValue: String?,
        bio: String? = null,
        age: Int? = null,
        gender: String? = null,
        country: String? = null,
        displayName: String? = null,
        onResult: (Boolean, String?) -> Unit
    ) {
        socket?.emit("update_profile", JSONObject().apply {
            if (displayName != null) put("displayName", displayName)
            if (color != null) put("customHexColor", color)
            if (avatarUrl != null) put("avatarUrl", avatarUrl)
            if (statusValue != null) put("status", statusValue)
            if (bio != null) put("bio", bio)
            if (age != null) put("age", age)
            if (gender != null) put("gender", gender)
            if (country != null) put("country", country)
        }, Ack { res ->
            val o = res.firstOrNull() as? JSONObject
            if (o?.optBoolean("ok") == true) { o.optJSONObject("user")?.let { _me.value = parseUser(it) }; onResult(true, null) }
            else onResult(false, o?.optString("error"))
        })
    }

    fun moderate(action: String, targetUserId: String?, extra: JSONObject.() -> Unit = {}) {
        socket?.emit("mod_action", JSONObject().apply {
            put("action", action)
            if (targetUserId != null) put("targetUserId", targetUserId)
            extra()
        })
    }

    fun triggerQuiz() = moderate("trigger_quiz", null)

    fun lockRoom(what: String, value: Boolean) =
        moderate("lock_room", null) { put("what", what); put("value", value) }

    fun broadcast(text: String) =
        moderate("broadcast", null) { put("text", text) }

    fun promote(targetUserId: String, rank: String) =
        moderate("promote", targetUserId) { put("rank", rank) }

    fun unbanDevice(deviceHash: String) =
        moderate("unban_device", null) { put("deviceHash", deviceHash) }

    fun listStaff(onResult: (List<ChatUser>) -> Unit) {
        socket?.emit("list_staff", JSONObject(), Ack { res ->
            val staff = (res.firstOrNull() as? JSONObject)?.optJSONArray("staff")?.let { parseUsers(it) } ?: emptyList()
            onResult(staff)
        })
    }

    fun listBanned(onResult: (List<Pair<String, String>>) -> Unit) {
        socket?.emit("list_banned", JSONObject(), Ack { res ->
            val list = mutableListOf<Pair<String, String>>()
            val arr = (res.firstOrNull() as? JSONObject)?.optJSONArray("banned")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(Pair(o.optString("hash"), o.optString("by")))
                }
            }
            onResult(list)
        })
    }

    fun loadThreads() {
        socket?.emit("list_threads", JSONObject(), Ack { res ->
            (res.firstOrNull() as? JSONObject)?.optJSONArray("threads")?.let { _threads.value = parseThreads(it) }
        })
    }

    fun loadRequests() {
        socket?.emit("list_requests", JSONObject(), Ack { res ->
            (res.firstOrNull() as? JSONObject)?.optJSONArray("requests")?.let { _requests.value = parseThreads(it) }
        })
    }

    fun loadScores() {
        socket?.emit("list_scores", JSONObject(), Ack { res ->
            (res.firstOrNull() as? JSONObject)?.optJSONArray("scores")?.let { a ->
                _scores.value = (0 until a.length()).map {
                    val o = a.getJSONObject(it)
                    PmThread(
                        userId = o.optString("userId"), name = o.optString("name"),
                        rank = runCatching { UserRank.valueOf(o.optString("rank", "REGULAR")) }.getOrDefault(UserRank.REGULAR),
                        avatarUrl = o.optString("avatarUrl"), lastText = o.optInt("score").toString()
                    )
                }
            }
        })
    }

    fun sendRequest(toUserId: String) = socket?.emit("send_request", JSONObject().apply { put("toUserId", toUserId) })

    fun respondRequest(fromUserId: String, accept: Boolean) {
        socket?.emit("accept_request", JSONObject().apply { put("fromUserId", fromUserId); put("accept", accept) },
            Ack { _ -> loadRequests() })
    }

    fun clearNotifications() { _notifications.value = emptyList() }

    private fun parseThreads(a: JSONArray) = (0 until a.length()).map {
        val o = a.getJSONObject(it)
        PmThread(
            userId = o.optString("userId"), name = o.optString("name"),
            rank = runCatching { UserRank.valueOf(o.optString("rank", "REGULAR")) }.getOrDefault(UserRank.REGULAR),
            avatarUrl = o.optString("avatarUrl"), lastText = o.optString("lastText")
        )
    }

    /** Registers the FCM token so the server can push private messages/broadcasts offline. */
    fun registerPush(fcmToken: String) {
        socket?.emit("register_push", JSONObject().apply { put("pushToken", fcmToken) })
    }

    fun syncYoutube(videoId: String, statusValue: String) =
        socket?.emit("sync_youtube", JSONObject().apply { put("videoId", videoId); put("status", statusValue) })

    fun videoFinished(videoId: String) =
        socket?.emit("video_finished", JSONObject().apply { put("videoId", videoId) })

    fun clearError() { _errors.value = null }

    fun disconnect() {
        socket?.disconnect(); socket?.off(); socket = null
        _status.value = Status.DISCONNECTED
    }

    private fun parseUser(o: JSONObject): ChatUser {
        val oldNamesArr = o.optJSONArray("oldNames")
        val oldNamesList = (0 until (oldNamesArr?.length() ?: 0)).map { oldNamesArr!!.getString(it) }
        return ChatUser(
            id = o.optString("id"),
            name = o.optString("name"),
            avatarUrl = o.optString("avatarUrl"),
            rank = runCatching { UserRank.valueOf(o.optString("rank", "REGULAR")) }.getOrDefault(UserRank.REGULAR),
            customHexColor = o.optString("customHexColor").takeIf { it.isNotBlank() && it != "null" },
            deviceId = deviceHash,
            isMuted = o.optBoolean("isMuted"),
            isGhost = o.optBoolean("isGhost"),
            bio = o.optString("bio"),
            age = o.optInt("age").takeIf { it > 0 },
            gender = o.optString("gender"),
            country = o.optString("country", "العراق"),
            status = o.optString("status", "online"),
            isGuest = o.optBoolean("isGuest"),
            lockPrivate = o.optBoolean("lockPrivate"),
            muteNotifications = o.optBoolean("muteNotifications"),
            oldNames = oldNamesList
        )
    }

    private fun parseUsers(a: JSONArray) = (0 until a.length()).map { parseUser(a.getJSONObject(it)) }

    private fun parseMessage(o: JSONObject): ChatMessage {
        val mType = o.optString("mediaType").takeIf { it.isNotBlank() && it != "null" }
        val mUrl = o.optString("mediaUrl").takeIf { it.isNotBlank() && it != "null" }
        val aDur = o.optInt("audioDuration").takeIf { it > 0 }
        return ChatMessage(
            id = o.optString("id"),
            roomId = o.optString("roomId"),
            senderId = o.optString("senderId"),
            senderName = o.optString("senderName"),
            senderAvatar = o.optString("senderAvatar"),
            senderRank = runCatching { UserRank.valueOf(o.optString("senderRank", "REGULAR")) }.getOrDefault(UserRank.REGULAR),
            customHexColor = o.optString("customHexColor").takeIf { it.isNotBlank() && it != "null" },
            text = o.optString("text"),
            timestamp = o.optString("timestamp"),
            isGhost = o.optBoolean("isGhost"),
            senderGender = o.optString("senderGender"),
            mediaType = mType,
            mediaUrl = mUrl,
            audioDuration = aDur
        )
    }

    private fun parseMessages(a: JSONArray) = (0 until a.length()).map { parseMessage(a.getJSONObject(it)) }

    private fun parseRooms(a: JSONArray) = (0 until a.length()).map {
        val o = a.getJSONObject(it)
        ChatRoom(
            id = o.optString("id"), title = o.optString("title"), description = o.optString("description"),
            topic = o.optString("topic"), onlineCount = o.optInt("online"), isLocked = o.optBoolean("lockPublic"),
            requiredRank = o.optString("requiredRank", "REGULAR")
        )
    }
}
