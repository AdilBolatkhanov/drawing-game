package com.adil

import com.adil.routes.createRoomRoute
import com.adil.routes.gameWebSocketRoute
import com.adil.routes.getRoomsRoute
import com.adil.routes.joinRoomRoute
import com.adil.session.DrawingSession
import com.google.gson.Gson
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.*
import io.ktor.websocket.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

val server = DrawingServer()
val gson = Gson()

fun Application.module() {
    install(Sessions) {
        cookie<DrawingSession>("SESSION")
    }

    intercept(ApplicationCallPipeline.Features) {
        if (call.sessions.get<DrawingSession>() == null) {
            //TODO Handle the case when client_id is not passed
            val clientId = call.parameters["client_id"] ?: ""
            call.sessions.set(DrawingSession(clientId, generateNonce()))
        }
    }

    install(WebSockets)
    install(Routing) {
        createRoomRoute()
        getRoomsRoute()
        joinRoomRoute()
        gameWebSocketRoute()
    }

    install(ContentNegotiation) {
        gson {
        }
    }

    install(CallLogging)
}
