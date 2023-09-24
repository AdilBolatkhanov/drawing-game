package com.adil.data

import com.adil.gson
import com.adil.routes.draw.models.Ping
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
) {
    companion object {
        const val PING_FREQUENCY = 3000L
    }

    private var pingJob: Job? = null
    private var pingTime = 0L
    @Volatile
    private var pongTime = 0L

    fun startPinging() {
        pingJob?.cancel()
        pingJob = GlobalScope.launch {
            while (true) {
                sendPing()
                delay(PING_FREQUENCY)
            }
        }
    }

    fun receivePong() {
        pongTime = System.currentTimeMillis()
    }

    fun disconnect() {
        pingJob?.cancel()
    }

    private suspend fun sendPing() {
        pingTime = System.currentTimeMillis()
        socket.send(Frame.Text(gson.toJson(Ping())))
        delay(PING_FREQUENCY)
        if (pingTime - pongTime > PING_FREQUENCY) {
            server.playerLeft(clientId)
            pingJob?.cancel()
        }
    }
}













