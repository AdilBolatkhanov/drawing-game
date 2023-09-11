package com.adil.data

import io.ktor.http.cio.websocket.*

data class Player(
    val username: String,
    val clientId: String,
    var socket: WebSocketSession,
    var isDrawing: Boolean = false,
    var score: Int = 0,
    var rank: Int = 0,
)
