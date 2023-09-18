package com.adil.routes.draw.models

import com.adil.routes.draw.models.common.FrameType
import com.adil.routes.draw.models.common.RoomFrame

data class DrawAction(
    val action: String,
    override val type: FrameType = FrameType.TYPE_DRAW_ACTION
) : RoomFrame
