package com.adil.data.models

import com.adil.other.Constants.TYPE_DRAW_DATA

data class DrawData(
    val roomName: String,
    val color: String,
    val thickness: String,
    val fromX: String,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    // TODO Use enums
    val motionEvent: Int
): BaseModel(TYPE_DRAW_DATA)
