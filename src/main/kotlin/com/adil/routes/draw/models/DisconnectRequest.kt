package com.adil.routes.draw.models

import com.adil.routes.draw.models.common.FrameType
import com.adil.routes.draw.models.common.RoomFrame

data class DisconnectRequest(override val type: FrameType = FrameType.TYPE_DISCONNECT_REQUEST) : RoomFrame