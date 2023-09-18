package com.adil.routes.draw.models

import com.adil.routes.draw.models.common.FrameType
import com.adil.routes.draw.models.common.RoomFrame

data class NewWords(
    val newWords: List<String>,
    override val type: FrameType = FrameType.TYPE_NEW_WORDS
): RoomFrame