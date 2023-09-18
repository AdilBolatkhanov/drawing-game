package com.adil.other

import com.adil.routes.draw.models.ChatMessage

fun ChatMessage.matchesWord(word: String) : Boolean {
    return message.toLowerCase().trim() == word.toLowerCase().trim()
}