package com.adil.data.models

import com.adil.other.Constants.TYPE_PLAYERS_LIST

data class PlayersList(
    val players: List<PlayerData>
): BaseModel(TYPE_PLAYERS_LIST)