package com.adil.routes.draw

import com.adil.data.Player
import com.adil.data.Room
import com.adil.gson
import com.adil.routes.draw.models.*
import com.adil.routes.draw.models.common.FrameType
import com.adil.routes.draw.models.common.RoomFrame
import com.adil.server
import com.adil.session.DrawingSession
import com.google.gson.JsonParser
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach

fun Route.gameWebSocketRoute() {
    route("/ws/draw") {
        standardWebSocket { socket, clientId, message, payload ->
            when (payload) {
                is JoinRoomHandshake -> {
                    val room = server.rooms[payload.roomName]
                    if (room == null) {
                        val gameError = GameError(GameError.ErrorType.ERROR_ROOM_NOT_FOUND.type)
                        socket.send(Frame.Text(gson.toJson(gameError)))
                        return@standardWebSocket
                    }
                    val player = Player(
                        username = payload.username,
                        clientId = payload.clientId,
                        socket = socket
                    )
                    // TODO It creates two players -> in server and in room, why?
                    server.playerJoined(player)
                    if (!room.containsPlayer(player.username)) {
                        room.addPlayer(
                            clientId= player.clientId,
                            username = player.username,
                            socket = socket
                        )
                    } else {
                        // The case when we quickly reconnect during ping delay
                        val playerInRoom = room.players.find { it.clientId == clientId }
                        playerInRoom?.socket = socket
                    }
                }

                is ChosenWord -> {
                    val room = server.rooms[payload.roomName] ?: return@standardWebSocket
                    room.setWordAndSwitchToGameRunning(payload.chosenWord)
                }

                is DrawData -> {
                    val room = server.rooms[payload.roomName] ?: return@standardWebSocket
                    if (room.phase == Room.Phase.GAME_RUNNING) {
                        room.broadcastToAllExcept(message, clientId)
                        room.addSerializedDrawInfo(message)
                    }
                    room.lastDrawData = payload
                }

                is DrawAction -> {
                    val room = server.getRoomWithClientId(clientId) ?: return@standardWebSocket
                    room.broadcastToAllExcept(message, clientId)
                    room.addSerializedDrawInfo(message)
                }

                is ChatMessage -> {
                    val room = server.rooms[payload.roomName] ?: return@standardWebSocket
                    if (!room.checkWordAndNotifyPlayers(payload)) {
                        room.broadcast(message)
                    }
                }

                is Ping -> {
                    server.players[clientId]?.receivePong()
                }

                is DisconnectRequest -> {
                    server.playerLeft(clientId)
                }
            }
        }
    }
}

fun Route.standardWebSocket(
    handleFrame: suspend (
        socket: DefaultWebSocketServerSession,
        clientId: String,
        message: String,
        payload: RoomFrame
    ) -> Unit
) {
    webSocket {
        val session = call.sessions.get<DrawingSession>()
        if (session == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No session"))
            return@webSocket
        }
        try {
            incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val message = frame.readText()
                    val jsonObject = JsonParser.parseString(message).asJsonObject
                    // TODO Polymorphic serialization
                    val type = when (jsonObject.get("type").asString) {
                        FrameType.TYPE_CHAT_MESSAGE.name -> ChatMessage::class.java
                        FrameType.TYPE_DRAW_DATA.name -> DrawData::class.java
                        FrameType.TYPE_JOIN_ROOM_HANDSHAKE.name -> JoinRoomHandshake::class.java
                        FrameType.TYPE_CHOSEN_WORD.name -> ChosenWord::class.java
                        FrameType.TYPE_PING.name -> Ping::class.java
                        FrameType.TYPE_DISCONNECT_REQUEST.name -> DisconnectRequest::class.java
                        FrameType.TYPE_DRAW_ACTION.name -> DrawAction::class.java
                        else -> RoomFrame::class.java
                    }
                    val payload = gson.fromJson(message, type)
                    handleFrame(this, session.clientId, message, payload)
                }
            }
        } catch (e: Exception) {
            println(e.localizedMessage)
        }
    }
}