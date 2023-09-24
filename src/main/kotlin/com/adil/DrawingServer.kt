package com.adil

import com.adil.data.Player
import com.adil.data.Room
import java.util.concurrent.ConcurrentHashMap

class DrawingServer {
    val rooms = ConcurrentHashMap<String, Room>()
    val players = ConcurrentHashMap<String, Player>()

    fun playerJoined(player: Player) {
        players[player.clientId]?.disconnect()
        players[player.clientId] = player
        player.startPinging()
    }

    fun playerLeft(clientId: String) {
        val playersRoom = getRoomWithClientId(clientId)
        println("Closing connection to ${players[clientId]?.username}")
        playersRoom?.removePlayer(clientId)
        players[clientId]?.disconnect()
        players.remove(clientId)
    }

    private fun getRoomWithClientId(clientId: String): Room? {
        val filteredRooms = rooms.filterValues { room ->
            room.players.find { player ->
                player.clientId == clientId
            } != null
        }
        return if (filteredRooms.values.isEmpty()) {
            null
        } else {
            filteredRooms.values.toList()[0]
        }
    }
}