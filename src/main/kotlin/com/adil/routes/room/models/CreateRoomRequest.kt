package com.adil.routes.room.models

data class CreateRoomRequest(
    val name: String,
    val maxPlayers: Int
)