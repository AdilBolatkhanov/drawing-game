package com.adil.routes.draw.models

import com.adil.routes.draw.models.common.FrameType
import com.adil.routes.draw.models.common.RoomFrame

data class JoinRoomHandshake(
    val username: String,
    val roomName: String,
    val clientId: String,
    override val type: FrameType = FrameType.TYPE_JOIN_ROOM_HANDSHAKE
) : RoomFrame