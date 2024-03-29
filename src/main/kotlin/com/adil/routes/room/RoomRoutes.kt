package com.adil.routes.room

import com.adil.data.Room
import com.adil.other.Constants.MAX_ROOM_SIZE
import com.adil.routes.room.models.BasicApiResponse
import com.adil.routes.room.models.CreateRoomRequest
import com.adil.routes.room.models.JoinRoomRequest
import com.adil.routes.room.models.RoomResponse
import com.adil.server
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*

// TODO Create proper structure of endpoints with extensions
fun Route.createRoomRoute() {
    route("/api/createRoom") {
        post {
            val roomRequest = call.receiveOrNull<CreateRoomRequest>()
            if (roomRequest == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing parameters")
                return@post
            }
            if (server.rooms[roomRequest.name] != null) {
                call.respond(
                    HttpStatusCode.OK,
                    BasicApiResponse(false, "Room already exists.")
                )
            }
            if(roomRequest.maxPlayers < 2) {
                call.respond(
                    HttpStatusCode.OK,
                    BasicApiResponse(false, "The minimum room size is 2.")
                )
                return@post
            }
            if(roomRequest.maxPlayers > MAX_ROOM_SIZE) {
                call.respond(
                    HttpStatusCode.OK,
                    BasicApiResponse(false, "The maximum room size is $MAX_ROOM_SIZE")
                )
                return@post
            }
            val room = Room(
                name = roomRequest.name,
                maxPlayers = roomRequest.maxPlayers
            )
            server.rooms[roomRequest.name] = room
            println("Room created ${roomRequest.name}")

            call.respond(HttpStatusCode.OK, BasicApiResponse(true))
        }
    }
}

fun Route.getRoomsRoute() {
    route("/api/getRooms") {
        get {
            val searchQuery = call.parameters["searchQuery"]
            if (searchQuery == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing parameters")
                return@get
            }

            val roomsResult = server.rooms.filterKeys {
                it.contains(searchQuery, ignoreCase = false)
            }
            val roomResponses = roomsResult.values.map { room ->
                RoomResponse(
                    name = room.name,
                    maxPlayers = room.maxPlayers,
                    playerCount = room.players.size
                )
            }.sortedBy { it.name }

            call.respond(HttpStatusCode.OK, roomResponses)
        }
    }
}

fun Route.joinRoomRoute() {
    route("/api/joinRoom") {
        get {
            val request = call.receiveOrNull<JoinRoomRequest>()
            if (request == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing parameters")
                return@get
            }

            val room = server.rooms[request.roomName]
            when {
                room == null -> {
                    call.respond(
                        HttpStatusCode.OK,
                        BasicApiResponse(false, "Room not found.")
                    )
                }

                room.players.containsKey(request.clientId) -> {
                    call.respond(
                        HttpStatusCode.OK,
                        BasicApiResponse(false, "A player with this client id already joined")
                    )
                }
                // TODO Handle the case when multiple users join simultaneously
                room.players.size >= room.maxPlayers -> {
                    call.respond(
                        HttpStatusCode.OK,
                        BasicApiResponse(false, "The room is already full")
                    )
                }
                else -> {
                    call.respond(HttpStatusCode.OK, BasicApiResponse(true))
                }
            }
        }
    }
}