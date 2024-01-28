package com.adil.routes.draw.models

import com.adil.routes.draw.models.common.FrameType
import com.adil.routes.draw.models.common.RoomFrame
import com.google.gson.annotations.SerializedName

data class GameError(
    val errorType: ErrorType,
    override val type: FrameType = FrameType.TYPE_GAME_ERROR
): RoomFrame {
    enum class ErrorType(val value: Int) {
        @SerializedName("0")
        ERROR_ROOM_NOT_FOUND(0)
    }
}