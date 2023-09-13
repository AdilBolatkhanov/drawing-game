package com.adil.data

import com.adil.data.models.Ping
import com.adil.gson
import com.adil.other.Constants.PING_FREQUENCY
import com.adil.server
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class Player(
    val username: String,
    val clientId: String,
    var socket: WebSocketSession,
    var isDrawing: Boolean = false,
    var score: Int = 0,
    var rank: Int = 0,
) {
    private var pingJob: Job? = null

    private var pingTime = 0L
    private var pongTime = 0L

    var isOnline = true

    fun startPinging() {
        pingJob?.cancel()
        pingJob = GlobalScope.launch {
            while (true) {
                sendPing()
                delay(PING_FREQUENCY)
            }
        }
    }

    private suspend fun sendPing() {
        pingTime = System.currentTimeMillis()
        socket.send(Frame.Text(gson.toJson(Ping())))
        delay(PING_FREQUENCY)
        if (pingTime - pongTime > PING_FREQUENCY) {
            isOnline = false
            server.playerLeft(clientId)
            pingJob?.cancel()
        }
    }

    fun receivePong() {
        pongTime = System.currentTimeMillis()
        isOnline = true
    }

    fun disconnect() {
        pingJob?.cancel()
    }
}













