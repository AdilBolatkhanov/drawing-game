package com.adil.data

import com.adil.data.models.*
import com.adil.gson
import com.adil.other.getRandowWords
import com.adil.other.matchesWord
import com.adil.other.transformToUnderscores
import com.adil.other.words
import com.adil.server
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

// TODO Make each instance of this class thread safe - concurrency
data class Room(
    val name: String,
    val maxPlayers: Int,
    var players: List<Player> = listOf()
) {
    var phase = Phase.WAITING_FOR_PLAYERS
        set(value) {
            synchronized(field) {
                field = value
                phaseChangeListener?.let { change ->
                    change(value)
                }
            }
        }

    var lastDrawData: DrawData? = null

    private var timerJob: Job? = null
    private var drawingPlayer: Player? = null
    private var winningPlayers = listOf<String>()
    private var word: String? = null
    private var curWords: List<String>? = null
    private var drawingPlayerIndex = 0
    private var startTime = 0L

    private val playerRemoveJobs = ConcurrentHashMap<String, Job>()
    // TODO data class instead of pair
    private val leftPlayers = ConcurrentHashMap<String, Pair<Player, Int>>()

    private var curRoundDrawData : List<String> = listOf()

    // TODO Get rid of this listener
    private var phaseChangeListener: ((Phase) -> Unit)? = null

    init {
        setPhaseChangedListener { newPhase ->
            when(newPhase) {
                Phase.WAITING_FOR_PLAYERS -> waitingForPlayers()
                Phase.WAITING_FOR_START -> waitingForStart()
                Phase.NEW_ROUND -> newRound()
                Phase.GAME_RUNNING -> gameRunning()
                Phase.SHOW_WORD -> showWord()
            }
        }
    }

    suspend fun broadcast(message: String) {
        players.forEach { player ->
            if (player.socket.isActive){
                player.socket.send(Frame.Text(message))
            }
        }
    }

    suspend fun broadcastToAllExcept(message: String, clientId: String) {
        players.forEach { player ->
            if (player.clientId != clientId && player.socket.isActive){
                player.socket.send(Frame.Text(message))
            }
        }
    }

    suspend fun checkWordAndNotifyPlayers(message: ChatMessage): Boolean {
        if (isGuessCorrect(message)) {
            val guessingTime = System.currentTimeMillis() - startTime
            val timePercentageLeft = 1L - guessingTime.toFloat() / DELAY_GAME_RUNNING_TO_SHOW_WORD
            val score = GUESS_SCORE_DEFAULT + GUESS_SCORE_PERCENTAGE_MULTIPLIER * timePercentageLeft
            val player = players.find { it.username == message.from }

            player?.let {
                it.score += score.toInt()
            }
            drawingPlayer?.let {
                it.score += GUESS_SCORE_FOR_DRAWING_PLAYER / players.size
            }

            broadcastPlayerStates()

            val announcement = Announcement(
                "${message.from} has guessed it!",
                System.currentTimeMillis(),
                Announcement.TYPE_PLAYER_GUESSED_WORD
            )
            broadcast(gson.toJson(announcement))
            val isRoundOver = addWinningPlayer(message.from)
            if (isRoundOver){
                val roundOverAnnouncement = Announcement(
                    "Everybody guessed it! New round is starting..",
                    System.currentTimeMillis(),
                    Announcement.TYPE_EVERYBODY_GUESSED_IT
                )
                broadcast(gson.toJson(roundOverAnnouncement))
            }
            return true
        }
        return false
    }

    suspend fun addPlayer(clientId: String, username: String, socket: WebSocketSession): Player {
        // TODO Do we need to restore player to its previous index? Do we need ordering of the players?
        var indexToAdd = players.size - 1
        val player = if (leftPlayers.containsKey(clientId)) {
            val leftPlayer = leftPlayers[clientId]
            leftPlayer?.first?.let { prevSavedCurPlayer ->
                prevSavedCurPlayer.socket = socket
                prevSavedCurPlayer.isDrawing = drawingPlayer?.clientId == clientId
                indexToAdd = leftPlayer.second

                playerRemoveJobs[clientId]?.cancel()
                playerRemoveJobs.remove(clientId)
                leftPlayers.remove(clientId)
                prevSavedCurPlayer
            } ?: Player(username, clientId, socket)
        } else {
            Player(username, clientId, socket)
        }
        indexToAdd = when {
            players.isEmpty() -> 0
            indexToAdd >= players.size -> players.size - 1
            else -> indexToAdd
        }
        val tmpPlayers = players.toMutableList()
        tmpPlayers.add(indexToAdd, player)
        players = tmpPlayers.toList()

        if (players.size == 1) {
            phase = Phase.WAITING_FOR_PLAYERS
        } else if (players.size == 2){
            phase = Phase.WAITING_FOR_START
            // TODO Do we really need to shuffle?
            players = players.shuffled()
        } else if (phase == Phase.WAITING_FOR_START && players.size == maxPlayers) {
            phase = Phase.NEW_ROUND
            players = players.shuffled()
        }

        sendWordToPlayer(player)
        broadcastPlayerStates()
        sendCurRoundDrawInfoToPlayer(player)

        val announcement = Announcement(
            "$username joined the party!",
            System.currentTimeMillis(),
            Announcement.TYPE_PLAYER_JOINED
        )
        broadcast(gson.toJson(announcement))
        return player
    }

    fun removePlayer(clientId: String) {
        val player = players.find { it.clientId == clientId } ?: return
        val index = players.indexOf(player)
        leftPlayers[clientId] = player to index
        players = players - player

        playerRemoveJobs[clientId] = GlobalScope.launch {
            delay(PLAYER_REMOVE_TIME)
            val playerToRemove = leftPlayers[clientId]
            leftPlayers.remove(clientId)
            playerToRemove?.let { (player, _) ->
                players = players - player
            }
            playerRemoveJobs.remove(clientId)
        }

        val announcement = Announcement(
            "${player.username} left the party",
            System.currentTimeMillis(),
            Announcement.TYPE_PLAYER_LEFT
        )

        GlobalScope.launch {
            broadcastPlayerStates()
            broadcast(gson.toJson(announcement))
            if (players.size == 1) {
                phase = Phase.WAITING_FOR_PLAYERS
                timerJob?.cancel()
            } else if (players.isEmpty()) {
                kill()
                server.rooms.remove(name)
            }
        }
    }

    fun containsPlayer(username: String) : Boolean {
        return players.find { it.username == username } != null
    }

    fun addSerializedDrawInfo(drawAction: String) {
        curRoundDrawData = curRoundDrawData + drawAction
    }

    fun setWordAndSwitchToGameRunning(word: String) {
        this.word = word
        phase = Phase.GAME_RUNNING
    }

    private fun setPhaseChangedListener(listener: (Phase) -> Unit) {
        phaseChangeListener = listener
    }

    private suspend fun sendCurRoundDrawInfoToPlayer(player: Player) {
        if (phase == Phase.GAME_RUNNING || phase == Phase.SHOW_WORD) {
            player.socket.send(Frame.Text(gson.toJson(RoundDrawInfo(curRoundDrawData))))
        }
    }

    private suspend fun broadcastPlayerStates() {
        val playersList = players.sortedByDescending { it.score }.mapIndexed { index, player ->
            PlayerData(player.username, player.isDrawing, player.score, index + 1)
        }
        broadcast(gson.toJson(PlayersList(playersList)))
    }

    private suspend fun finishOffDrawing() {
        lastDrawData?.let {
            if (curRoundDrawData.isNotEmpty() && it.motionEvent == 2) {
                val finishDrawData = it.copy(motionEvent = 1)
                broadcast(gson.toJson(finishDrawData))
            }
        }
    }

    private suspend fun sendWordToPlayer(player: Player) {
        val delay = when (phase){
            Phase.WAITING_FOR_START -> DELAY_WAITING_FOR_START_TO_NEW_ROUND
            Phase.NEW_ROUND -> DELAY_NEW_ROUND_TO_GAME_RUNNING
            Phase.GAME_RUNNING -> DELAY_GAME_RUNNING_TO_SHOW_WORD
            Phase.SHOW_WORD -> DELAY_SHOW_WORD_TO_NEW_ROUND
            else -> 0L
        }
        val phaseChange = PhaseChange(phase, delay, drawingPlayer?.username)

        word?.let { curWord ->
            drawingPlayer?.let { drawingPlayer ->
                val gameState = GameState(
                    drawingPlayer.username,
                    // TODO Maybe just check player == drawingPlayer
                    if (player.isDrawing || phase == Phase.SHOW_WORD) {
                        curWord
                    } else {
                        curWord.transformToUnderscores()
                    }
                )
                player.socket.send(Frame.Text(gson.toJson(gameState)))
            }
        }
        player.socket.send(Frame.Text(gson.toJson(phaseChange)))
    }

    private fun timeAndNotify(ms: Long) {
        timerJob?.cancel()
        // TODO Our own scope, think about it
        timerJob = GlobalScope.launch {
            startTime = System.currentTimeMillis()
            val phaseChange = PhaseChange(
                phase,
                ms,
                drawingPlayer?.username
            )
            repeat((ms / UPDATE_TIME_FREQUENCY).toInt()) {
                if (it != 0) {
                    phaseChange.phase = null
                }
                broadcast(gson.toJson(phaseChange))
                phaseChange.time -= UPDATE_TIME_FREQUENCY
                delay(UPDATE_TIME_FREQUENCY)
            }
            phase = when (phase) {
                Phase.WAITING_FOR_START -> Phase.NEW_ROUND
                Phase.NEW_ROUND -> {
                    word = null
                    Phase.GAME_RUNNING
                }
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
        // TODO Our own scope, think about it
        GlobalScope.launch {
            val phaseChange = PhaseChange(
                Phase.WAITING_FOR_PLAYERS,
                DELAY_WAITING_FOR_START_TO_NEW_ROUND
            )
            broadcast(gson.toJson(phaseChange))
        }
    }

    private fun waitingForStart() {
        GlobalScope.launch {
            timeAndNotify(DELAY_WAITING_FOR_START_TO_NEW_ROUND)
            val phaseChange = PhaseChange(
                Phase.WAITING_FOR_START,
                DELAY_WAITING_FOR_START_TO_NEW_ROUND
            )
            broadcast(gson.toJson(phaseChange))
        }
    }

    private fun newRound() {
        curRoundDrawData = listOf()
        curWords = getRandowWords(3)
        val newWords = NewWords(curWords!!)
        nextDrawingPlayer()
        GlobalScope.launch {
            broadcastPlayerStates()
            drawingPlayer?.socket?.send(Frame.Text(gson.toJson(newWords)))
            timeAndNotify(DELAY_NEW_ROUND_TO_GAME_RUNNING)
        }
    }

    private fun gameRunning() {
        winningPlayers = listOf()
        val wordToSend = word ?: curWords?.random() ?: words.random()
        word = wordToSend
        val wordWithUnderscores = wordToSend.transformToUnderscores()
        val drawingUsername = (drawingPlayer ?: players.random()).username
        val gameStateForDrawingPlayer = GameState(
            drawingUsername,
            wordToSend
        )
        val gameStateForGuessingPlayers = GameState(
            drawingUsername,
            wordWithUnderscores
        )
        GlobalScope.launch {
            broadcastToAllExcept(
                gson.toJson(gameStateForGuessingPlayers),
                drawingPlayer?.clientId ?: players.random().clientId
            )
            drawingPlayer?.socket?.send(Frame.Text(gson.toJson(gameStateForDrawingPlayer)))

            timeAndNotify(DELAY_GAME_RUNNING_TO_SHOW_WORD)
            println("Drawing phase in room $name started. It'll last ${DELAY_GAME_RUNNING_TO_SHOW_WORD / 1000}s")
        }
    }

    private fun nextDrawingPlayer() {
        drawingPlayer?.isDrawing = false
        if (players.isEmpty()) {
            return
        }

        drawingPlayer = if (drawingPlayerIndex <= players.size - 1) {
            players[drawingPlayerIndex]
        } else players.last()

        if (drawingPlayerIndex < players.size - 1) drawingPlayerIndex++
        else drawingPlayerIndex = 0

        drawingPlayer?.isDrawing = true
     }

    private fun addWinningPlayer(username: String): Boolean {
        winningPlayers = winningPlayers + username
        if (winningPlayers.size == players.size - 1){
            phase = Phase.NEW_ROUND
            return true
        }
        return false
    }

    private fun isGuessCorrect(guess: ChatMessage): Boolean {
        // TODO Handle the case when player who guessed the word before
        //  doesn't send it again to the whole group
        return guess.matchesWord(word ?: return false) &&
                !winningPlayers.contains(guess.from) &&
                guess.from != drawingPlayer?.username && phase == Phase.GAME_RUNNING
    }

    private fun showWord() {
        GlobalScope.launch {
            if (winningPlayers.isEmpty()) {
                drawingPlayer?.let {
                    it.score -= PENALTY_NOBODY_GUESSED_IT
                }
            }
            broadcastPlayerStates()
            word?.let {
                val chosenWord = ChosenWord(it, name)
                broadcast(gson.toJson(chosenWord))
            }
            timeAndNotify(DELAY_SHOW_WORD_TO_NEW_ROUND)
            val phaseChange = PhaseChange(Phase.SHOW_WORD, DELAY_SHOW_WORD_TO_NEW_ROUND)
            broadcast(gson.toJson(phaseChange))
        }
    }

    private fun kill() {
        playerRemoveJobs.values.forEach { it.cancel() }
        timerJob?.cancel()
    }

    // TODO Make sealed class instead
    enum class Phase {
        WAITING_FOR_PLAYERS,
        WAITING_FOR_START,
        NEW_ROUND,
        GAME_RUNNING,
        SHOW_WORD
    }

    companion object {

        const val UPDATE_TIME_FREQUENCY = 1000L

        const val PLAYER_REMOVE_TIME = 60000L

        const val DELAY_WAITING_FOR_START_TO_NEW_ROUND = 10000L
        const val DELAY_NEW_ROUND_TO_GAME_RUNNING = 20000L
        const val DELAY_GAME_RUNNING_TO_SHOW_WORD = 60000L
        const val DELAY_SHOW_WORD_TO_NEW_ROUND = 10000L

        const val PENALTY_NOBODY_GUESSED_IT = 50
        const val GUESS_SCORE_DEFAULT = 50
        const val GUESS_SCORE_PERCENTAGE_MULTIPLIER = 50
        const val GUESS_SCORE_FOR_DRAWING_PLAYER = 50
    }
}











