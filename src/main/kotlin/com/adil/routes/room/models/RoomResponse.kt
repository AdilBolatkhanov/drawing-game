package com.adil.routes.room.models

data class RoomResponse(
    val name: String,
    val maxPlayers: Int,
    val playerCount: Int
)