package com.adil

import com.adil.plugins.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.websocket.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    install(ContentNegotiation) {
        gson {
        }
    }

    install(CallLogging)
    install(WebSockets)
}
