package com.adil.routes.draw.models

import com.adil.routes.draw.models.common.FrameType
import com.adil.routes.draw.models.common.RoomFrame

data class RoundDrawInfo(
    val data: List<String>,
    override val type: FrameType = FrameType.TYPE_CUR_ROUND_DRAW_INFO
) : RoomFrame
