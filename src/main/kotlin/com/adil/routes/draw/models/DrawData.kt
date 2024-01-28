package com.adil.routes.draw.models

import com.adil.routes.draw.models.common.FrameType
import com.adil.routes.draw.models.common.RoomFrame
import com.google.gson.annotations.SerializedName

data class DrawData(
    val roomName: String,
    val color: String,
    val thickness: String,
    val fromX: String,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val motionEvent: MotionType,
    override val type: FrameType = FrameType.TYPE_DRAW_DATA
): RoomFrame

enum class MotionType(val value: Int) {
    @SerializedName("0")
    UP(0),

    @SerializedName("1")
    DOWN(1),

    @SerializedName("2")
    MOVE(2),
}
