package com.example.game

import com.example.data.GameRoom
import com.example.data.RoomStatus
import com.example.data.SeatColor
import com.example.data.TurnPhase

data class GridCell(val col: Int, val row: Int)

data class MoveOutcome(
    val updatedTokens: List<Int>,
    val nextTurnSeat: Int,
    val nextDiceValue: Int,
    val nextTurnPhase: TurnPhase,
    val nextConsecutiveSixes: Int,
    val winnerSeat: Int,
    val nextStatus: RoomStatus,
    val capturedSeat: Int?,
    val eventText: String
)

/**
 * Classic 11x11 Vintage Persian Mensch (Mensch ärgere Dich nicht) Geometry & Rules Engine.
 * - 40-cell clockwise perimeter loop (indices 0..39)
 * - 4 corner home yards (2x2 circles per seat)
 * - 4 colored inner finish columns (4 circles per seat, progress 40..43)
 * - Progress per pawn: -1 (yard), 0..39 (main track relative to seat start), 40..43 (finish column)
 */
object MenchRulesEngine {

    const val BOARD_SIZE = 11
    const val TRACK_LENGTH = 40
    const val MAX_PROGRESS = 43
    const val PAWNS_PER_SEAT = 4

    // Exact 40-cell clockwise outer loop on an 11x11 classic Mensch board
    val mainTrackCells: List<GridCell> = listOf(
        // Red arm (bottom-left going UP -> left arm going LEFT)
        GridCell(4, 10), // 0  - Red Start
        GridCell(4, 9),  // 1
        GridCell(4, 8),  // 2
        GridCell(4, 7),  // 3
        GridCell(4, 6),  // 4
        GridCell(3, 6),  // 5
        GridCell(2, 6),  // 6
        GridCell(1, 6),  // 7
        GridCell(0, 6),  // 8
        GridCell(0, 5),  // 9  - Blue Home Entry Turn
        // Blue arm (left going RIGHT -> top arm going UP)
        GridCell(0, 4),  // 10 - Blue Start
        GridCell(1, 4),  // 11
        GridCell(2, 4),  // 12
        GridCell(3, 4),  // 13
        GridCell(4, 4),  // 14
        GridCell(4, 3),  // 15
        GridCell(4, 2),  // 16
        GridCell(4, 1),  // 17
        GridCell(4, 0),  // 18
        GridCell(5, 0),  // 19 - Yellow Home Entry Turn
        // Yellow arm (top going DOWN -> right arm going RIGHT)
        GridCell(6, 0),  // 20 - Yellow Start
        GridCell(6, 1),  // 21
        GridCell(6, 2),  // 22
        GridCell(6, 3),  // 23
        GridCell(6, 4),  // 24
        GridCell(7, 4),  // 25
        GridCell(8, 4),  // 26
        GridCell(9, 4),  // 27
        GridCell(10, 4), // 28
        GridCell(10, 5), // 29 - Green Home Entry Turn
        // Green arm (right going LEFT -> bottom arm going DOWN)
        GridCell(10, 6), // 30 - Green Start
        GridCell(9, 6),  // 31
        GridCell(8, 6),  // 32
        GridCell(7, 6),  // 33
        GridCell(6, 6),  // 34
        GridCell(6, 7),  // 35
        GridCell(6, 8),  // 36
        GridCell(6, 9),  // 37
        GridCell(6, 10), // 38
        GridCell(5, 10)  // 39 - Red Home Entry Turn
    )

    // 4 colored finish columns (4 circles per seat, indices 0..3 -> progress 40..43)
    val homeColumnCells: Map<Int, List<GridCell>> = mapOf(
        0 to listOf(GridCell(5, 9), GridCell(5, 8), GridCell(5, 7), GridCell(5, 6)), // Red (bottom -> up)
        1 to listOf(GridCell(1, 5), GridCell(2, 5), GridCell(3, 5), GridCell(4, 5)), // Blue (left -> right)
        2 to listOf(GridCell(5, 1), GridCell(5, 2), GridCell(5, 3), GridCell(5, 4)), // Yellow (top -> down)
        3 to listOf(GridCell(9, 5), GridCell(8, 5), GridCell(7, 5), GridCell(6, 5))  // Green (right -> left)
    )

    // 4 corner home yards (2x2 circles per seat)
    val yardCells: Map<Int, List<GridCell>> = mapOf(
        0 to listOf(GridCell(0, 9), GridCell(1, 9), GridCell(0, 10), GridCell(1, 10)), // Red (bottom-left)
        1 to listOf(GridCell(0, 0), GridCell(1, 0), GridCell(0, 1), GridCell(1, 1)),   // Blue (top-left)
        2 to listOf(GridCell(9, 0), GridCell(10, 0), GridCell(9, 1), GridCell(10, 1)), // Yellow (top-right)
        3 to listOf(GridCell(9, 9), GridCell(10, 9), GridCell(9, 10), GridCell(10, 10)) // Green (bottom-right)
    )

    fun startTrackIndex(seat: Int): Int = (seat * 10) % TRACK_LENGTH

    fun toGlobalTrackIndex(seat: Int, progress: Int): Int? {
        if (progress !in 0 until TRACK_LENGTH) return null
        return (startTrackIndex(seat) + progress) % TRACK_LENGTH
    }

    fun tokenGridCell(seat: Int, tokenIndex: Int, progress: Int): GridCell {
        return when {
            progress < 0 -> yardCells[seat]?.getOrNull(tokenIndex) ?: GridCell(0, 0)
            progress < TRACK_LENGTH -> {
                val globalIndex = (startTrackIndex(seat) + progress) % TRACK_LENGTH
                mainTrackCells[globalIndex]
            }
            else -> {
                val homeIdx = (progress - TRACK_LENGTH).coerceIn(0, 3)
                homeColumnCells[seat]?.getOrNull(homeIdx) ?: GridCell(5, 5)
            }
        }
    }

    fun getSeatTokens(tokens: List<Int>, seat: Int): List<Int> {
        val base = seat * PAWNS_PER_SEAT
        return List(PAWNS_PER_SEAT) { offset -> tokens.getOrElse(base + offset) { -1 } }
    }

    fun getMovableTokenIndices(tokens: List<Int>, seat: Int, dice: Int): List<Int> {
        if (dice !in 1..6) return emptyList()
        val myTokens = getSeatTokens(tokens, seat)
        val result = mutableListOf<Int>()
        for (i in 0 until PAWNS_PER_SEAT) {
            val current = myTokens[i]
            val target = if (current == -1) {
                if (dice == 6) 0 else continue
            } else {
                current + dice
            }
            if (target > MAX_PROGRESS) continue
            // Cannot land on one of your own pawns
            val occupiedBySelf = myTokens.indices.any { other -> other != i && myTokens[other] == target }
            if (!occupiedBySelf) {
                result.add(i)
            }
        }
        return result
    }

    fun applyMove(
        room: GameRoom,
        seat: Int,
        tokenIndex: Int,
        dice: Int
    ): MoveOutcome {
        val tokens = room.normalizedTokens().toMutableList()
        val seatNames = room.normalizedSeatNames()
        val actorName = seatNames.getOrElse(seat) { SeatColor.fromIndex(seat).persianName }
        val base = seat * PAWNS_PER_SEAT
        val currentProgress = tokens[base + tokenIndex]
        val targetProgress = if (currentProgress == -1) 0 else currentProgress + dice

        tokens[base + tokenIndex] = targetProgress

        // Check opponent capture on outer 40-cell ring
        var capturedSeat: Int? = null
        val globalTarget = toGlobalTrackIndex(seat, targetProgress)
        if (globalTarget != null) {
            for (otherSeat in 0 until 4) {
                if (otherSeat == seat) continue
                val otherBase = otherSeat * PAWNS_PER_SEAT
                for (t in 0 until PAWNS_PER_SEAT) {
                    val otherProg = tokens[otherBase + t]
                    val otherGlobal = toGlobalTrackIndex(otherSeat, otherProg)
                    if (otherGlobal == globalTarget) {
                        tokens[otherBase + t] = -1
                        capturedSeat = otherSeat
                    }
                }
            }
        }

        // Check victory (all 4 pawns inside finish column 40..43)
        val hasWon = (0 until PAWNS_PER_SEAT).all { idx -> tokens[base + idx] >= TRACK_LENGTH }
        if (hasWon) {
            return MoveOutcome(
                updatedTokens = tokens,
                nextTurnSeat = seat,
                nextDiceValue = 0,
                nextTurnPhase = TurnPhase.FINISHED,
                nextConsecutiveSixes = 0,
                winnerSeat = seat,
                nextStatus = RoomStatus.FINISHED,
                capturedSeat = capturedSeat,
                eventText = "$actorName با بردن همه مهره‌ها به خانه قهرمان شد!"
            )
        }

        val bonusRoll = (dice == 6 && room.consecutiveSixes < 3) || capturedSeat != null
        val nextSeat = if (bonusRoll) seat else (seat + 1) % 4
        val nextSixes = if (dice == 6 && bonusRoll) room.consecutiveSixes else 0

        val eventMessage = when {
            capturedSeat != null -> {
                val victimName = seatNames.getOrElse(capturedSeat) { SeatColor.fromIndex(capturedSeat).persianName }
                "$actorName مهره $victimName را زد و جایزه پرتاب گرفت!"
            }
            currentProgress == -1 -> "$actorName با تاس ۶ یک مهره وارد صفحه کرد و دوباره تاس می‌ریزد."
            targetProgress >= TRACK_LENGTH && currentProgress < TRACK_LENGTH ->
                "$actorName یک مهره را به خانه امن رساند!"
            bonusRoll -> "$actorName با تاس ۶ جایزه پرتاب مجدد گرفت."
            else -> "$actorName مهره خود را $dice خانه جلو برد."
        }

        return MoveOutcome(
            updatedTokens = tokens,
            nextTurnSeat = nextSeat,
            nextDiceValue = 0,
            nextTurnPhase = TurnPhase.ROLL,
            nextConsecutiveSixes = nextSixes,
            winnerSeat = -1,
            nextStatus = RoomStatus.PLAYING,
            capturedSeat = capturedSeat,
            eventText = eventMessage
        )
    }

    fun chooseBotTokenIndex(tokens: List<Int>, seat: Int, dice: Int): Int? {
        val movable = getMovableTokenIndices(tokens, seat, dice)
        if (movable.isEmpty()) return null
        if (movable.size == 1) return movable.first()

        val base = seat * PAWNS_PER_SEAT
        return movable.maxByOrNull { idx ->
            val current = tokens[base + idx]
            val target = if (current == -1) 0 else current + dice
            var score = 0

            // 1. Highest priority: capture an opponent
            val globalTarget = toGlobalTrackIndex(seat, target)
            if (globalTarget != null) {
                val capturesOpponent = (0 until 4).any { otherSeat ->
                    otherSeat != seat && (0 until PAWNS_PER_SEAT).any { t ->
                        toGlobalTrackIndex(otherSeat, tokens[otherSeat * PAWNS_PER_SEAT + t]) == globalTarget
                    }
                }
                if (capturesOpponent) score += 1000
            }

            // 2. Enter finish column safely
            if (current < TRACK_LENGTH && target >= TRACK_LENGTH) score += 700

            // 3. Bring pawn out of yard on 6
            if (current == -1 && dice == 6) score += 500

            // 4. Advance furthest pawn toward finish
            score += target * 5
            score
        }
    }
}
