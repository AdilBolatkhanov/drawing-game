package com.adil

import com.adil.other.PROGRAMMERS_WORDLIST
import com.adil.other.fillWords
import com.adil.routes.draw.gameWebSocketRoute
import com.adil.routes.room.createRoomRoute
import com.adil.routes.room.getRoomsRoute
import com.adil.routes.room.joinRoomRoute
import com.adil.session.DrawingSession
import com.google.gson.Gson
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.*
import io.ktor.websocket.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

val server = DrawingServer()
val gson = Gson()

fun Application.module() {
    fillWords(PROGRAMMERS_WORDLIST)

    install(Sessions) {
        cookie<DrawingSession>("SESSION")
    }

    intercept(ApplicationCallPipeline.Features) {
        if (call.sessions.get<DrawingSession>() == null) {
            val clientId = call.parameters["client_id"] ?: return@intercept call.respond(HttpStatusCode.BadRequest, "Client id is not passed")
            call.sessions.set(DrawingSession(clientId = clientId, sessionId = generateNonce()))
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
