package com.digibuddy.core.socket

import android.util.Log
import com.digibuddy.core.utils.Constants
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URI

class SocketManager private constructor() {

    private var socket: Socket? = null
    private val TAG = "SocketManager"

    fun connect(token: String) {
        try {
            val options = IO.Options.builder()
                .setAuth(mapOf("token" to token))
                .setReconnection(true)
                .setReconnectionAttempts(5)
                .build()
            socket = IO.socket(URI.create(Constants.SOCKET_URL), options)
            socket?.connect()

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Socket connected: ${socket?.id()}")
            }
            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "Socket disconnected")
            }
            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e(TAG, "Socket connect error: ${args.firstOrNull()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Socket connection error", e)
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }

    fun isConnected() = socket?.connected() == true

    fun joinRoom(roomId: String) {
        socket?.emit("join:room", roomId)
    }

    fun sendMessage(roomId: String, content: String, type: String = "TEXT") {
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("content", content)
            put("type", type)
        }
        socket?.emit("message:send", data)
    }

    fun sendTypingStart(roomId: String) = socket?.emit("typing:start", roomId)
    fun sendTypingStop(roomId: String) = socket?.emit("typing:stop", roomId)

    fun updateAvailability(isAvailable: Boolean) {
        socket?.emit("helper:availability", isAvailable)
    }

    fun onNewMessage(callback: (JSONObject) -> Unit) {
        socket?.on("message:new") { args ->
            (args.firstOrNull() as? JSONObject)?.let(callback)
        }
    }

    fun onTyping(callback: (String, Boolean) -> Unit) {
        socket?.on("typing:user") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            val userId = data.optString("userId")
            val isTyping = data.optBoolean("isTyping")
            callback(userId, isTyping)
        }
    }

    fun onBookingNew(callback: (JSONObject) -> Unit) {
        socket?.on("booking:new") { args ->
            (args.firstOrNull() as? JSONObject)?.let(callback)
        }
    }

    fun onBookingStatus(callback: (String, String) -> Unit) {
        socket?.on("booking:status") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            callback(data.optString("bookingId"), data.optString("status"))
        }
    }

    fun off(event: String) = socket?.off(event)

    companion object {
        @Volatile private var instance: SocketManager? = null
        fun getInstance(): SocketManager =
            instance ?: synchronized(this) { instance ?: SocketManager().also { instance = it } }
    }
}
