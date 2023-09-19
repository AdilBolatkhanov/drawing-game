package com.adil.routes.draw.models

import com.adil.routes.draw.models.common.FrameType
import com.adil.routes.draw.models.common.RoomFrame
import com.google.gson.annotations.SerializedName

data class Announcement(
    val message: String,
    val timestamp: Long,
    val announcementType: Type,
    override val type: FrameType = FrameType.TYPE_ANNOUNCEMENT
): RoomFrame {
    enum class Type(val value: Int) {
        @SerializedName("0")
        PLAYER_GUESSED_WORD(0),

        @SerializedName("1")
        PLAYER_JOINED(1),

        @SerializedName("2")
        PLAYER_LEFT(2),

        @SerializedName("3")
        EVERYBODY_GUESSED_IT(3),
    }
}
