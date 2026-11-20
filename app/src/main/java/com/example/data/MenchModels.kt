package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class RoomStatus {
    WAITING,
    PLAYING,
    FINISHED;

    companion object {
        fun fromRaw(raw: String?): RoomStatus =
            entries.firstOrNull { it.name == raw } ?: WAITING
    }
}

enum class TurnPhase {
    ROLL,
    MOVE,
    FINISHED;

    companion object {
        fun fromRaw(raw: String?): TurnPhase =
            entries.firstOrNull { it.name == raw } ?: ROLL
    }
}

enum class SeatColor(val index: Int, val persianName: String) {
    RED(0, "قرمز"),
    BLUE(1, "آبی"),
    YELLOW(2, "زرد"),
    GREEN(3, "سبز");

    companion object {
        fun fromIndex(index: Int): SeatColor =
            entries.firstOrNull { it.index == index } ?: RED
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "بازیکن منچ",
    val wins: Int = 0,
    val gamesPlayed: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GameRoom(
    @DocumentId val id: String = "",
    val roomCode: String = "",
    val roomName: String = "",
    val hostUid: String = "",
    val publicLobby: Boolean = true,
    val status: String = RoomStatus.WAITING.name,
    val lobbyKey: String = "PUBLIC_WAITING",
    val memberUids: List<String> = emptyList(),
    val seatUids: List<String> = listOf("", "", "", ""),
    val seatNames: List<String> = listOf("صندلی آزاد", "صندلی آزاد", "صندلی آزاد", "صندلی آزاد"),
    val seatBotFlags: List<Boolean> = listOf(false, false, false, false),
    val tokens: List<Int> = List(16) { -1 },
    val currentTurnSeat: Int = 0,
    val diceValue: Int = 0,
    val turnPhase: String = TurnPhase.ROLL.name,
    val consecutiveSixes: Int = 0,
    val winnerSeat: Int = -1,
    val lastEventText: String = "",
    val voiceEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun roomStatusEnum(): RoomStatus = RoomStatus.fromRaw(status)

    fun turnPhaseEnum(): TurnPhase = TurnPhase.fromRaw(turnPhase)

    fun seatOf(uid: String): Int = seatUids.indexOf(uid)

    fun normalizedTokens(): List<Int> =
        if (tokens.size == 16) tokens else List(16) { idx -> tokens.getOrElse(idx) { -1 } }

    fun normalizedSeatUids(): List<String> =
        if (seatUids.size == 4) seatUids else List(4) { idx -> seatUids.getOrElse(idx) { "" } }

    fun normalizedSeatNames(): List<String> =
        if (seatNames.size == 4) seatNames else List(4) { idx -> seatNames.getOrElse(idx) { "صندلی آزاد" } }

    fun normalizedBotFlags(): List<Boolean> =
        if (seatBotFlags.size == 4) seatBotFlags else List(4) { idx -> seatBotFlags.getOrElse(idx) { false } }
}

@Keep
@IgnoreExtraProperties
data class VoicePacket(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val senderSeat: Int = 0,
    val memberUids: List<String> = emptyList(),
    val audioBase64: String = "",
    val durationMs: Int = 0,
    val sampleRate: Int = 8000,
    @ServerTimestamp val createdAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
