package com.adil.data

import com.adil.gson
import com.adil.other.getRandomWords
import com.adil.other.matchesWord
import com.adil.other.transformToUnderscores
import com.adil.other.words
import com.adil.routes.draw.models.*
import com.adil.server
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

// TODO Make each instance of this class thread safe - concurrency
// TODO Think about tradeoffs between immutable and mutable data classes in project
data class Room(
    val name: String,
    val maxPlayers: Int,
    val players: ConcurrentHashMap<String, Player> = ConcurrentHashMap()
) {
    @Volatile
    private var phase = Phase.WAITING_FOR_PLAYERS
        set(value) {
            field = value
            when (value) {
                Phase.WAITING_FOR_PLAYERS -> waitingForPlayers()
                Phase.WAITING_FOR_START -> waitingForStart()
                Phase.NEW_ROUND -> newRound()
                Phase.GAME_RUNNING -> gameRunning()
                Phase.SHOW_WORD -> showWord()
            }
        }

    private var timerJob: Job? = null

    @Volatile
    private var drawingPlayer: Player? = null
    private val winningPlayers = Collections.synchronizedSet<String>(HashSet())

    @Volatile
    private var word: String? = null
    private var curWords: List<String>? = null

    private val curRoundDrawData = mutableListOf<String>()
    private var lastDrawData: DrawData? = null

    private var startTimeOfCurrentPhase = 0L

    private val playerRemoveJobs = ConcurrentHashMap<String, Job>()
    private val leftPlayers = ConcurrentHashMap<String, Player>()

    suspend fun broadcast(message: String) {
        players.values.forEach { player ->
            if (player.socket.isActive) {
                player.socket.send(Frame.Text(message))
            }
        }
    }

    suspend fun broadcastToAllExcept(message: String, clientId: String) {
        players.values.forEach { player ->
            if (player.clientId != clientId && player.socket.isActive) {
                player.socket.send(Frame.Text(message))
            }
        }
    }

    suspend fun checkWordAndNotifyPlayers(message: ChatMessage): Boolean {
        if (isGuessCorrect(message)) {
            val score = calculateTheScore()
            val player = players[message.clientId] ?: return true
            player.score.getAndAdd(score)

            drawingPlayer?.score?.getAndAdd(GUESS_SCORE_FOR_DRAWING_PLAYER / players.size)

            broadcastPlayerStates()

            val announcement = Announcement(
                message = "${player.username} has guessed it!",
                timestamp = System.currentTimeMillis(),
                announcementType = Announcement.Type.PLAYER_GUESSED_WORD
            )
            broadcast(gson.toJson(announcement))

            val isRoundOver = addWinningPlayer(player.clientId)
            if (isRoundOver) {
                val roundOverAnnouncement = Announcement(
                    message = "Everybody guessed it! New round is starting..",
                    timestamp = System.currentTimeMillis(),
                    announcementType = Announcement.Type.EVERYBODY_GUESSED_IT
                )
                broadcast(gson.toJson(roundOverAnnouncement))
            }
            return true
        }
        return false
    }

    suspend fun addPlayer(newPlayer: Player) {
        val newOrRestoredPlayer = if (leftPlayers.containsKey(newPlayer.clientId)) {
            leftPlayers[newPlayer.clientId]?.let { prevSavedCurPlayer ->
                prevSavedCurPlayer.socket = newPlayer.socket
                prevSavedCurPlayer.isDrawing = drawingPlayer?.clientId == newPlayer.clientId

                playerRemoveJobs[newPlayer.clientId]?.cancel()
                playerRemoveJobs.remove(newPlayer.clientId)
                leftPlayers.remove(newPlayer.clientId)
                prevSavedCurPlayer
            } ?: newPlayer
        } else {
            newPlayer
        }
        players[newPlayer.clientId] = newOrRestoredPlayer
        newOrRestoredPlayer.startPinging()

        // TODO Add mutex or handle the case when multiple users added at the same time
        if (players.size == 1) {
            phase = Phase.WAITING_FOR_PLAYERS
        } else if (players.size == 2) {
            phase = Phase.WAITING_FOR_START
        } else if (phase == Phase.WAITING_FOR_START && players.size == maxPlayers) {
            phase = Phase.NEW_ROUND
        }

        val phaseChange =
            PhaseChange(phase = phase, time = phase.delayToNextPhase, drawingPlayer = drawingPlayer?.username)
        newPlayer.socket.send(Frame.Text(gson.toJson(phaseChange)))

        sendWordToPlayer(newOrRestoredPlayer)
        broadcastPlayerStates()
        sendCurRoundDrawInfoToPlayer(newOrRestoredPlayer)

        val announcement = Announcement(
            message = "${newOrRestoredPlayer.username} joined the party!",
            timestamp = System.currentTimeMillis(),
            announcementType = Announcement.Type.PLAYER_JOINED
        )
        broadcast(gson.toJson(announcement))
    }

    fun removePlayer(clientId: String) {
        val player = players[clientId] ?: return
        leftPlayers[clientId] = player
        players.remove(clientId)
        player.disconnect()

        playerRemoveJobs[clientId] = GlobalScope.launch {
            delay(PLAYER_REMOVE_TIME)

            val playerToRemove = leftPlayers[clientId] ?: return@launch
            leftPlayers.remove(playerToRemove.clientId)
            players.remove(playerToRemove.clientId)
            playerToRemove.disconnect()

            playerRemoveJobs.remove(playerToRemove.clientId)
        }

        val announcement = Announcement(
            message = "${player.username} left the party",
            timestamp = System.currentTimeMillis(),
            announcementType = Announcement.Type.PLAYER_LEFT
        )

        GlobalScope.launch {
            broadcastPlayerStates()
            broadcast(gson.toJson(announcement))
            if (players.size == 1) {
                phase = Phase.WAITING_FOR_PLAYERS
                timerJob?.cancel()
            } else if (players.isEmpty()) {
                killOngoingJobs()
                server.rooms.remove(name)
            }
        }
    }

    suspend fun drawDataReceived(message: String, clientId: String, payload: DrawData) {
        if (phase == Phase.GAME_RUNNING) {
            broadcastToAllExcept(message, clientId)
            curRoundDrawData += message
            lastDrawData = payload
        }
    }

    suspend fun drawActionReceived(message: String, clientId: String) {
        if (phase == Phase.GAME_RUNNING) {
            broadcastToAllExcept(message, clientId)
            curRoundDrawData += message
        }
    }

    fun setWordAndSwitchToGameRunning(word: String) {
        this.word = word
        phase = Phase.GAME_RUNNING
    }

    private suspend fun sendCurRoundDrawInfoToPlayer(player: Player) {
        if (phase == Phase.GAME_RUNNING || phase == Phase.SHOW_WORD) {
            player.socket.send(Frame.Text(gson.toJson(RoundDrawInfo(curRoundDrawData))))
        }
    }

    private fun calculateTheScore(): Int {
        val guessingTime = System.currentTimeMillis() - startTimeOfCurrentPhase
        val timePercentageLeft = 1L - guessingTime.toFloat() / Phase.GAME_RUNNING.delayToNextPhase
        return (GUESS_SCORE_DEFAULT + GUESS_SCORE_PERCENTAGE_MULTIPLIER * timePercentageLeft).toInt()
    }

    private suspend fun broadcastPlayerStates() {
        val playersList = players.values.sortedByDescending { it.score.get() }.mapIndexed { index, player ->
            PlayerData(
                username = player.username,
                isDrawing = player.isDrawing,
                score = player.score.get(),
                rank = index + 1
            )
        }
        broadcast(gson.toJson(PlayersList(playersList)))
    }

    private suspend fun finishOffDrawing() {
        lastDrawData?.let { drawData ->
            if (curRoundDrawData.isNotEmpty() && drawData.motionEvent == MotionType.MOVE) {
                val finishDrawData = drawData.copy(motionEvent = MotionType.DOWN)
                broadcast(gson.toJson(finishDrawData))
            }
        }
    }

    private suspend fun sendWordToPlayer(player: Player) {
        val curRoundWord = word ?: return
        val curDrawingPlayer = drawingPlayer ?: return
        val gameState = GameState(
            drawingPlayer = curDrawingPlayer.username,
            word = if (player.isDrawing || phase == Phase.SHOW_WORD) {
                curRoundWord
            } else {
                curRoundWord.transformToUnderscores()
            }
        )
        player.socket.send(Frame.Text(gson.toJson(gameState)))
    }

    private fun timeAndNotify() {
        timerJob?.cancel()

        // TODO Our own scope, think about it
        timerJob = GlobalScope.launch {
            startTimeOfCurrentPhase = System.currentTimeMillis()

            periodicNotifyPhaseParameterChanges()

            phase = when (phase) {
                Phase.WAITING_FOR_START -> Phase.NEW_ROUND
                Phase.NEW_ROUND -> Phase.GAME_RUNNING
                Phase.GAME_RUNNING -> {
                    finishOffDrawing()
                    Phase.SHOW_WORD
                }

                Phase.SHOW_WORD -> Phase.NEW_ROUND
                else -> Phase.WAITING_FOR_PLAYERS
            }
        }
    }

    private fun waitingForPlayers() {
        GlobalScope.launch {
            val phaseChange = PhaseChange(
                phase = Phase.WAITING_FOR_PLAYERS,
                time = Phase.WAITING_FOR_PLAYERS.delayToNextPhase
            )
            broadcast(gson.toJson(phaseChange))
        }
    }

    private fun waitingForStart() {
        GlobalScope.launch {
            timeAndNotify()
        }
    }

    private fun newRound() {
        word = null
        curRoundDrawData.clear()
        lastDrawData = null
        winningPlayers.clear()

        GlobalScope.launch {
            nextDrawingPlayer()

            broadcastPlayerStates()

            curWords = getRandomWords(3)
            val newWords = NewWords(curWords!!)
            drawingPlayer?.socket?.send(Frame.Text(gson.toJson(newWords)))

            timeAndNotify()
        }
    }

    private fun gameRunning() {
        val wordToSend = word ?: curWords?.random() ?: words.random()
        word = wordToSend
        GlobalScope.launch {
            val drawingUsername = drawingPlayer?.username.orEmpty()

            val gameStateForGuessingPlayers = GameState(
                drawingPlayer = drawingUsername,
                word = wordToSend.transformToUnderscores()
            )
            broadcastToAllExcept(
                gson.toJson(gameStateForGuessingPlayers),
                drawingPlayer?.clientId.toString()
            )

            val gameStateForDrawingPlayer = GameState(
                drawingPlayer = drawingUsername,
                word = wordToSend
            )
            drawingPlayer?.socket?.send(Frame.Text(gson.toJson(gameStateForDrawingPlayer)))

            timeAndNotify()
            println("Drawing phase in room $name started. It'll last ${Phase.GAME_RUNNING.delayToNextPhase / 1000}s")
        }
    }

    private fun showWord() {
        GlobalScope.launch {
            if (winningPlayers.isEmpty()) {
                drawingPlayer?.score?.getAndAdd(-PENALTY_NOBODY_GUESSED_IT)
            }

            broadcastPlayerStates()
            word?.let { targetWord ->
                val chosenWord = ChosenWord(chosenWord = targetWord, roomName = name)
                broadcast(gson.toJson(chosenWord))
            }
            timeAndNotify()
        }
    }

    private suspend fun Room.periodicNotifyPhaseParameterChanges() {
        val delay = phase.delayToNextPhase
        val phaseChange = PhaseChange(
            phase = phase,
            time = delay,
            drawingPlayer = drawingPlayer?.username
        )
        repeat((delay / UPDATE_TIME_FREQUENCY).toInt()) { passedSeconds ->
            if (passedSeconds != 0) {
                phaseChange.phase = null
            }
            broadcast(gson.toJson(phaseChange))
            phaseChange.time -= UPDATE_TIME_FREQUENCY
            delay(UPDATE_TIME_FREQUENCY)
        }
    }

    private fun nextDrawingPlayer() {
        drawingPlayer?.isDrawing = false
        if (players.isEmpty()) {
            return
        }
        drawingPlayer = players.values.random()
        drawingPlayer?.isDrawing = true
    }

    private fun addWinningPlayer(username: String): Boolean {
        winningPlayers += username
        if (winningPlayers.size == players.size - 1) {
            phase = Phase.NEW_ROUND
            return true
        }
        return false
    }

    private fun isGuessCorrect(guess: ChatMessage): Boolean {
        // TODO Handle the case when player who guessed the word before
        //  doesn't send it again to the whole group
        return guess.matchesWord(word ?: return false) &&
                !winningPlayers.contains(guess.clientId) &&
                guess.clientId != drawingPlayer?.clientId && phase == Phase.GAME_RUNNING
    }

    private fun killOngoingJobs() {
        playerRemoveJobs.values.forEach { it.cancel() }
        timerJob?.cancel()
    }

    enum class Phase(val delayToNextPhase: Long) {
        WAITING_FOR_PLAYERS(0L),
        WAITING_FOR_START(10000L),
        NEW_ROUND(20000L),
        GAME_RUNNING(60000L),
        SHOW_WORD(10000L)
    }

    companion object {

        const val UPDATE_TIME_FREQUENCY = 1000L

        const val PLAYER_REMOVE_TIME = 60000L

        const val PENALTY_NOBODY_GUESSED_IT = 50
        const val GUESS_SCORE_DEFAULT = 50
        const val GUESS_SCORE_PERCENTAGE_MULTIPLIER = 50
        const val GUESS_SCORE_FOR_DRAWING_PLAYER = 50
    }
}











