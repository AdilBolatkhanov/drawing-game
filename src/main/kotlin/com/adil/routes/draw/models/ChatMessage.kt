package com.adil.routes.draw.models

import com.adil.routes.draw.models.common.FrameType
import com.adil.routes.draw.models.common.RoomFrame

data class ChatMessage(
    val from: String,
    val roomName: String,
    val message: String,
    val timestamp: Long,
    override val type: FrameType = FrameType.TYPE_CHAT_MESSAGE
): RoomFrame
