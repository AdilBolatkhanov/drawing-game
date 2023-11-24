package com.adil.routes.draw.models

import com.adil.routes.draw.models.common.FrameType
import com.adil.routes.draw.models.common.RoomFrame

data class PlayersList(
    val players: List<PlayerData>,
    override val type: FrameType = FrameType.TYPE_PLAYERS_LIST
): RoomFrame