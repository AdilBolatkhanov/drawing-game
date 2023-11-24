package com.adil.other

import com.adil.routes.draw.models.ChatMessage

fun ChatMessage.matchesWord(word: String) : Boolean {
    return message.lowercase().trim() == word.lowercase().trim()
}