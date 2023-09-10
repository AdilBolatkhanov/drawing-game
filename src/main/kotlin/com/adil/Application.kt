package com.adil

import com.adil.session.DrawingSession
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.sessions.*
import io.ktor.util.*
import io.ktor.websocket.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    install(Sessions) {
        cookie<DrawingSession>("SESSION")
    }

    intercept(ApplicationCallPipeline.Features) {
        if (call.sessions.get<DrawingSession>() == null) {
            val clientId = call.parameters["client_id"] ?: ""
            call.sessions.set(DrawingSession(clientId, generateNonce()))
        }
    }
    install(ContentNegotiation) {
        gson {
        }
    }

    install(CallLogging)
    install(WebSockets)
}