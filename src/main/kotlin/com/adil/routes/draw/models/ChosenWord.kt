package com.adil.routes.draw.models

import com.adil.routes.draw.models.common.FrameType
import com.adil.routes.draw.models.common.RoomFrame

data class ChosenWord(
    val chosenWord: String,
    val roomName: String,
    override val type: FrameType = FrameType.TYPE_CHOSEN_WORD
) : RoomFrame
