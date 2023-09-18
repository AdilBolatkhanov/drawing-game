package com.adil.routes.draw.models

import com.adil.routes.draw.models.common.FrameType
import com.adil.routes.draw.models.common.RoomFrame

data class Announcement(
    val message: String,
    val timestamp: Long,
    val announcementType: Int,
    override val type: FrameType = FrameType.TYPE_ANNOUNCEMENT
): RoomFrame {
    enum class Type(val value: Int) {
        PLAYER_GUESSED_WORD(0),
        PLAYER_JOINED(1),
        PLAYER_LEFT(2),
        EVERYBODY_GUESSED_IT(3),
    }
}
