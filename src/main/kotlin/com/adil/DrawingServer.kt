package com.adil

import com.adil.data.Player
import com.adil.data.Room
import java.util.concurrent.ConcurrentHashMap

class DrawingServer {
    val rooms = ConcurrentHashMap<String, Room>()

    suspend fun playerJoined(player: Player, room: Room) {
        if (!room.players.containsKey(player.clientId)) {
            room.addPlayer(player)
        } else {
            // The case when we quickly reconnect during ping delay
            val playerInRoom = room.players[player.clientId]
            playerInRoom?.socket = player.socket
        }
    }

    fun playerLeft(clientId: String) {
        val playersRoom = getRoomWithClientId(clientId)
        println("Closing connection to ${playersRoom?.players?.get(clientId)?.username}")
        playersRoom?.removePlayer(clientId)
    }

    fun playerReceivedPing(clientId: String) {
        val playersRoom = getRoomWithClientId(clientId)
        playersRoom?.players?.get(clientId)?.receivePong()
    }

    private fun getRoomWithClientId(clientId: String): Room? {
        val filteredRooms = rooms.filterValues { room ->
            room.players[clientId] != null
        }
        return if (filteredRooms.values.isEmpty()) {
            null
        } else {
            filteredRooms.values.toList()[0]
        }
    }
}