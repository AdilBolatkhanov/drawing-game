package com.adil.routes.draw.models

import com.adil.data.Room
import com.adil.routes.draw.models.common.FrameType
import com.adil.routes.draw.models.common.RoomFrame

// TODO Think about tradeoffs between immutable and mutable data classes in project
data class PhaseChange(
    var phase: Room.Phase?,
    var time: Long,
    val drawingPlayer: String? = null,
    override val type: FrameType = FrameType.TYPE_PHASE_CHANGE
) : RoomFrame