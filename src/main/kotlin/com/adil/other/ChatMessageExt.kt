package com.adil.other

import com.adil.data.models.ChatMessage

fun ChatMessage.matchesWord(word: String) : Boolean {
    return message.toLowerCase().trim() == word.toLowerCase().trim()
}