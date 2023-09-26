package com.adil.routes.room.models

data class JoinRoomRequest(
    val clientId: String,
    val roomName: String
)