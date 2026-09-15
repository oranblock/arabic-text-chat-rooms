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
    private val _youtubeId = MutableStateFlow<String?>("jfKfPfyJRdk")
    val youtubeId: StateFlow<String?> = _youtubeId
    private val _youtubeTitle = MutableStateFlow<String?>("موسيقى هادئة - ديوانية العراق 🎵")
    val youtubeTitle: StateFlow<String?> = _youtubeTitle
    private val _notifications = MutableStateFlow<List<String>>(emptyList())
    val notifications: StateFlow<List<String>> = _notifications
    private val _threads = MutableStateFlow<List<PmThread>>(emptyList())
    val threads: StateFlow<List<PmThread>> = _threads
    private val _requests = MutableStateFlow<List<PmThread>>(emptyList())
    val requests: StateFlow<List<PmThread>> = _requests

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
                    if (vId.isNotBlank()) _youtubeId.value = vId
                    val vTitle = obj.optString("videoTitle")
                    if (vTitle.isNotBlank()) _youtubeTitle.value = vTitle
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

    fun register(name: String, password: String, onResult: (Boolean, String?) -> Unit) {
        ensureSocket {
            socket?.emit("register", JSONObject().apply { put("name", name); put("password", password) },
                Ack { res -> handleAuth(res.firstOrNull(), onResult) })
        }
    }

    fun login(name: String?, password: String?, savedToken: String?, onResult: (Boolean, String?) -> Unit) {
        ensureSocket {
            val payload = JSONObject().apply {
                if (name != null) put("name", name)
                if (password != null) put("password", password)
                if (savedToken != null) put("token", savedToken)
            }
            socket?.emit("login", payload, Ack { res -> handleAuth(res.firstOrNull(), onResult) })
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
                if (yId.isNotBlank()) _youtubeId.value = yId
                val yTitle = r.optString("youtubeTitle")
                if (yTitle.isNotBlank()) _youtubeTitle.value = yTitle
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

    fun sendMessage(text: String) = socket?.emit("send_message", JSONObject().apply { put("text", text) })
    fun sendPrivate(toUserId: String, text: String) = socket?.emit("private_send", JSONObject().apply { put("toUserId", toUserId); put("text", text) })
    fun loadPrivateHistory(withUserId: String) {
        socket?.emit("private_history", JSONObject().apply { put("withUserId", withUserId) }, Ack { res ->
            (res.firstOrNull() as? JSONObject)?.optJSONArray("messages")?.let { _privates.value = parseMessages(it) }
        })
    }

    fun updateProfile(color: String?, avatarUrl: String?, statusValue: String?, onResult: (Boolean, String?) -> Unit) {
        socket?.emit("update_profile", JSONObject().apply {
            if (color != null) put("customHexColor", color)
            if (avatarUrl != null) put("avatarUrl", avatarUrl)
            if (statusValue != null) put("status", statusValue)
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

    fun clearError() { _errors.value = null }

    fun disconnect() {
        socket?.disconnect(); socket?.off(); socket = null
        _status.value = Status.DISCONNECTED
    }

    private fun parseUser(o: JSONObject) = ChatUser(
        id = o.optString("id"),
        name = o.optString("name"),
        avatarUrl = o.optString("avatarUrl"),
        rank = runCatching { UserRank.valueOf(o.optString("rank", "REGULAR")) }.getOrDefault(UserRank.REGULAR),
        customHexColor = o.optString("customHexColor").takeIf { it.isNotBlank() && it != "null" },
        deviceId = deviceHash,
        isMuted = o.optBoolean("isMuted"),
        isGhost = o.optBoolean("isGhost")
    )

    private fun parseUsers(a: JSONArray) = (0 until a.length()).map { parseUser(a.getJSONObject(it)) }

    private fun parseMessage(o: JSONObject) = ChatMessage(
        id = o.optString("id"),
        roomId = o.optString("roomId"),
        senderId = o.optString("senderId"),
        senderName = o.optString("senderName"),
        senderAvatar = o.optString("senderAvatar"),
        senderRank = runCatching { UserRank.valueOf(o.optString("senderRank", "REGULAR")) }.getOrDefault(UserRank.REGULAR),
        customHexColor = o.optString("customHexColor").takeIf { it.isNotBlank() && it != "null" },
        text = o.optString("text"),
        timestamp = o.optString("timestamp"),
        isGhost = o.optBoolean("isGhost")
    )

    private fun parseMessages(a: JSONArray) = (0 until a.length()).map { parseMessage(a.getJSONObject(it)) }

    private fun parseRooms(a: JSONArray) = (0 until a.length()).map {
        val o = a.getJSONObject(it)
        ChatRoom(
            id = o.optString("id"), title = o.optString("title"), description = o.optString("description"),
            topic = o.optString("topic"), onlineCount = o.optInt("online"), isLocked = o.optBoolean("lockPublic")
        )
    }
}
