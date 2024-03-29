package com.adil.other

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

var words = emptyList<String>()
const val PROGRAMMERS_WORDLIST = "resources/programmers_wordlist.txt"
fun fillWords(fileName: String) {
    GlobalScope.launch(Dispatchers.IO) {
        words = readWordList(fileName)
    }
}

fun readWordList(fileName: String): List<String> {
    return File(fileName).inputStream().use {
        val words = mutableListOf<String>()
        it.bufferedReader().forEachLine { word -> words.add(word) }
        words
    }
}

fun getRandomWords(amount: Int): List<String> {
    var curAmount = 0
    val result = mutableSetOf<String>()
    while (curAmount < amount) {
        val word = words.random()
        if (!result.contains(word)) {
            result.add(word)
            curAmount++
        }
    }
    return result.toList()
}

fun String.transformToUnderscores() =
    toCharArray().map {
        if (it != ' ') '_' else ' '
    }.joinToString(" ")
