package com.adil.data.models

data class PlayerData(
    val username: String,
    val isDrawing: Boolean = false,
    val score: Int = 0,
    val rank: Int = 0
)
