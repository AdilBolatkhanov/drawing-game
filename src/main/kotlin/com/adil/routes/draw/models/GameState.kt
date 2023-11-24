package com.adil.routes.draw.models

import com.adil.routes.draw.models.common.FrameType
import com.adil.routes.draw.models.common.RoomFrame

data class GameState(
    val drawingPlayer: String,
    val word: String,
    override val type: FrameType = FrameType.TYPE_GAME_STATE
): RoomFrame
