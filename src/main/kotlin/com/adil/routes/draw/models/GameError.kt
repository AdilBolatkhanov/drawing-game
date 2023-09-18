package com.adil.routes.draw.models

import com.adil.routes.draw.models.common.FrameType
import com.adil.routes.draw.models.common.RoomFrame

data class GameError(
    val errorType: Int,
    override val type: FrameType = FrameType.TYPE_GAME_ERROR
): RoomFrame {
    enum class ErrorType(val type: Int) {
        ERROR_ROOM_NOT_FOUND(0)
    }
}