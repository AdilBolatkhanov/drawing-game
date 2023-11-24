package com.adil.routes.draw.models

import com.adil.routes.draw.models.common.FrameType
import com.adil.routes.draw.models.common.RoomFrame

data class Ping(override val type: FrameType = FrameType.TYPE_PING) : RoomFrame