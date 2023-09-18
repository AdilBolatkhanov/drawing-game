package com.adil.routes.draw.models

import com.adil.routes.draw.models.common.FrameType
import com.adil.routes.draw.models.common.RoomFrame

data class DrawData(
    val roomName: String,
    val color: String,
    val thickness: String,
    val fromX: String,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    // TODO Use enums
    val motionEvent: Int,
    override val type: FrameType = FrameType.TYPE_DRAW_DATA
): RoomFrame
