package com.adil.data.models

import com.adil.data.Room
import com.adil.other.Constants.TYPE_PHASE_CHANGE

// TODO Think about tradeoffs between immutable and mutable data classes in project
data class PhaseChange(
    var phase: Room.Phase?,
    var time: Long,
    val drawingPlayer: String? = null
) : BaseModel(TYPE_PHASE_CHANGE)