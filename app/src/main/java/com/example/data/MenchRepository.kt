package com.example.data

import android.content.Context
import com.example.R
import com.example.game.MenchRulesEngine
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class MenchRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("برای دسترسی به بازی آنلاین باید با حساب گوگل وارد شوید.")
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "عدم تطابق شناسه کاربر" }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(displayNameRaw: String): Result<UserProfile> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val safeName = displayNameRaw.trim().ifBlank { "بازیکن منچ" }.take(40)
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val existing = docRef.get().await()
            if (existing.exists()) {
                val profile = existing.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(id = uid, userId = uid, displayName = safeName)
                Result.success(profile)
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "wins" to 0,
                    "gamesPlayed" to 0,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                Result.success(
                    UserProfile(
                        id = uid,
                        userId = uid,
                        displayName = safeName,
                        wins = 0,
                        gamesPlayed = 0
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateDisplayName(newName: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val clean = newName.trim().take(40)
            require(clean.isNotEmpty()) { "نام نمایشی نمی‌تواند خالی باشد" }
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "displayName" to clean,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordMatchResult(won: Boolean): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val updates = mutableMapOf<String, Any>(
                "gamesPlayed" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (won) {
                updates["wins"] = FieldValue.increment(1L)
            }
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    fun observePublicWaitingRooms(): Flow<List<GameRoom>> = flow {
        val uid = requireUserId()
        val path = "rooms"
        emitAll(
            db.collection(path)
                .whereEqualTo("lobbyKey", "PUBLIC_WAITING")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        GameRoom::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMyRooms(userId: String): Flow<List<GameRoom>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "عدم تطابق شناسه کاربر" }
        val path = "rooms"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        GameRoom::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeRoom(roomCode: String): Flow<GameRoom?> = flow {
        val uid = requireUserId()
        val code = roomCode.trim().uppercase()
        val path = "rooms/$code"
        emitAll(
            db.collection("rooms").document(code)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            GameRoom::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createRoom(
        roomNameRaw: String,
        publicLobby: Boolean,
        hostNameRaw: String
    ): Result<String> {
        var docPath = "rooms"
        return try {
            val uid = requireUserId()
            val roomCode = generateRoomCode()
            val roomName = roomNameRaw.trim().ifBlank { "میز منچ کلاسیک" }.take(40)
            val hostName = hostNameRaw.trim().ifBlank { "میزبان" }.take(40)
            val docRef = db.collection("rooms").document(roomCode)
            docPath = docRef.path

            val lobbyKey = if (publicLobby) "PUBLIC_WAITING" else "PRIVATE_WAITING"
            val payload = mapOf(
                "roomCode" to roomCode,
                "roomName" to roomName,
                "hostUid" to uid,
                "publicLobby" to publicLobby,
                "status" to RoomStatus.WAITING.name,
                "lobbyKey" to lobbyKey,
                "memberUids" to listOf(uid),
                "seatUids" to listOf(uid, "", "", ""),
                "seatNames" to listOf(hostName, "صندلی آزاد", "صندلی آزاد", "صندلی آزاد"),
                "seatBotFlags" to listOf(false, false, false, false),
                "tokens" to List(16) { -1 },
                "currentTurnSeat" to 0,
                "diceValue" to 0,
                "turnPhase" to TurnPhase.ROLL.name,
                "consecutiveSixes" to 0,
                "winnerSeat" to -1,
                "lastEventText" to "$hostName میز را ساخت؛ منتظر بازیکنان...",
                "voiceEnabled" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(roomCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun joinRoom(roomCodeRaw: String, playerNameRaw: String): Result<String> {
        val code = roomCodeRaw.trim().uppercase()
        val docPath = "rooms/$code"
        return try {
            val uid = requireUserId()
            require(code.matches(Regex("^[A-Z0-9]{4,12}$"))) { "کد اتاق نامعتبر است" }
            val playerName = playerNameRaw.trim().ifBlank { "بازیکن" }.take(40)
            val docRef = db.collection("rooms").document(code)

            db.runTransaction { tx ->
                val snapshot = tx.get(docRef)
                if (!snapshot.exists()) {
                    throw IllegalStateException("اتاقی با کد $code پیدا نشد.")
                }
                val room = snapshot.toObject(
                    GameRoom::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: throw IllegalStateException("خطا در خواندن اطلاعات اتاق.")

                if (uid in room.memberUids) {
                    return@runTransaction code
                }
                if (room.roomStatusEnum() != RoomStatus.WAITING) {
                    throw IllegalStateException("این بازی قبلاً شروع شده یا به پایان رسیده است.")
                }
                val seatUids = room.normalizedSeatUids().toMutableList()
                val seatNames = room.normalizedSeatNames().toMutableList()
                val seatBotFlags = room.normalizedBotFlags().toMutableList()
                val emptySeat = seatUids.indexOfFirst { it.isBlank() }
                if (emptySeat == -1 || room.memberUids.size >= 4) {
                    throw IllegalStateException("ظرفیت این میز ۴ نفره تکمیل شده است.")
                }

                seatUids[emptySeat] = uid
                seatNames[emptySeat] = playerName
                seatBotFlags[emptySeat] = false
                val newMembers = room.memberUids + uid

                tx.update(
                    docRef,
                    mapOf(
                        "memberUids" to newMembers,
                        "seatUids" to seatUids,
                        "seatNames" to seatNames,
                        "seatBotFlags" to seatBotFlags,
                        "lastEventText" to "$playerName روی صندلی ${SeatColor.fromIndex(emptySeat).persianName} نشست.",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                code
            }.await()
            Result.success(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun leaveWaitingRoom(room: GameRoom): Result<Unit> {
        val docPath = "rooms/${room.roomCode}"
        return try {
            val uid = requireUserId()
            if (room.hostUid == uid) {
                db.collection("rooms").document(room.roomCode).delete().await()
                return Result.success(Unit)
            }
            if (room.roomStatusEnum() != RoomStatus.WAITING) {
                return Result.success(Unit)
            }
            val seatUids = room.normalizedSeatUids().toMutableList()
            val seatNames = room.normalizedSeatNames().toMutableList()
            val seatBotFlags = room.normalizedBotFlags().toMutableList()
            val mySeat = seatUids.indexOf(uid)
            if (mySeat >= 0) {
                seatUids[mySeat] = ""
                seatNames[mySeat] = "صندلی آزاد"
                seatBotFlags[mySeat] = false
            }
            val newMembers = room.memberUids.filterNot { it == uid }
            db.collection("rooms").document(room.roomCode).update(
                mapOf(
                    "memberUids" to newMembers,
                    "seatUids" to seatUids,
                    "seatNames" to seatNames,
                    "seatBotFlags" to seatBotFlags,
                    "lastEventText" to "یک بازیکن اتاق انتظار را ترک کرد.",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun startMatch(room: GameRoom): Result<Unit> {
        val docPath = "rooms/${room.roomCode}"
        return try {
            val uid = requireUserId()
            check(room.hostUid == uid) { "فقط میزبان می‌تواند بازی را شروع کند." }
            val seatUids = room.normalizedSeatUids().toMutableList()
            val seatNames = room.normalizedSeatNames().toMutableList()
            val seatBotFlags = room.normalizedBotFlags().toMutableList()

            val botLabels = listOf("ربات قرمز", "ربات آبی", "ربات زرد", "ربات سبز")
            for (i in 0 until 4) {
                if (seatUids[i].isBlank()) {
                    seatUids[i] = "BOT_$i"
                    seatNames[i] = botLabels[i]
                    seatBotFlags[i] = true
                }
            }

            db.collection("rooms").document(room.roomCode).update(
                mapOf(
                    "status" to RoomStatus.PLAYING.name,
                    "lobbyKey" to "PLAYING",
                    "seatUids" to seatUids,
                    "seatNames" to seatNames,
                    "seatBotFlags" to seatBotFlags,
                    "currentTurnSeat" to 0,
                    "diceValue" to 0,
                    "turnPhase" to TurnPhase.ROLL.name,
                    "consecutiveSixes" to 0,
                    "lastEventText" to "بازی آغاز شد! نوبت پرتاب تاس ${seatNames[0]} است.",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun submitDiceRoll(room: GameRoom, rolledValue: Int): Result<Unit> {
        val docPath = "rooms/${room.roomCode}"
        return try {
            requireUserId()
            require(rolledValue in 1..6) { "مقدار تاس نامعتبر است" }
            val seat = room.currentTurnSeat
            val actorName = room.normalizedSeatNames().getOrElse(seat) { "بازیکن" }
            val tokens = room.normalizedTokens()
            val nextSixes = if (rolledValue == 6) (room.consecutiveSixes + 1).coerceAtMost(3) else 0
            val movable = MenchRulesEngine.getMovableTokenIndices(tokens, seat, rolledValue)

            val updates: Map<String, Any> = if (movable.isEmpty()) {
                val nextSeat = (seat + 1) % 4
                val nextName = room.normalizedSeatNames().getOrElse(nextSeat) { "بازیکن بعدی" }
                mapOf(
                    "diceValue" to 0,
                    "turnPhase" to TurnPhase.ROLL.name,
                    "currentTurnSeat" to nextSeat,
                    "consecutiveSixes" to 0,
                    "lastEventText" to "$actorName تاس $rolledValue آورد اما حرکت ممکنی نداشت؛ نوبت $nextName.",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            } else {
                mapOf(
                    "diceValue" to rolledValue,
                    "turnPhase" to TurnPhase.MOVE.name,
                    "consecutiveSixes" to nextSixes,
                    "lastEventText" to "$actorName تاس $rolledValue آورد! یکی از مهره‌های روشن را انتخاب کنید.",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            }

            db.collection("rooms").document(room.roomCode).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun submitTokenMove(room: GameRoom, tokenIndex: Int): Result<Unit> {
        val docPath = "rooms/${room.roomCode}"
        return try {
            requireUserId()
            val seat = room.currentTurnSeat
            val dice = room.diceValue
            val movable = MenchRulesEngine.getMovableTokenIndices(room.normalizedTokens(), seat, dice)
            require(tokenIndex in movable) { "این مهره قابل حرکت نیست." }

            val outcome = MenchRulesEngine.applyMove(room, seat, tokenIndex, dice)
            val lobbyKey = if (outcome.nextStatus == RoomStatus.FINISHED) "FINISHED" else "PLAYING"

            db.collection("rooms").document(room.roomCode).update(
                mapOf(
                    "tokens" to outcome.updatedTokens,
                    "currentTurnSeat" to outcome.nextTurnSeat,
                    "diceValue" to outcome.nextDiceValue,
                    "turnPhase" to outcome.nextTurnPhase.name,
                    "consecutiveSixes" to outcome.nextConsecutiveSixes,
                    "winnerSeat" to outcome.winnerSeat,
                    "status" to outcome.nextStatus.name,
                    "lobbyKey" to lobbyKey,
                    "lastEventText" to outcome.eventText.take(160),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    fun observeVoicePackets(roomCode: String, userId: String): Flow<List<VoicePacket>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "عدم تطابق شناسه کاربر" }
        val path = "rooms/$roomCode/voice_packets"
        emitAll(
            db.collection("rooms").document(roomCode)
                .collection("voice_packets")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VoicePacket::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun sendVoicePacket(
        room: GameRoom,
        senderSeat: Int,
        senderNameRaw: String,
        audioBase64: String,
        durationMs: Int
    ): Result<Unit> {
        val packetRef = db.collection("rooms").document(room.roomCode)
            .collection("voice_packets").document()
        val docPath = packetRef.path
        return try {
            val uid = requireUserId()
            require(audioBase64.length in 8..120000) { "حجم بسته صوتی خارج از محدوده مجاز است" }
            val clampedDuration = durationMs.coerceIn(150, 8000)
            val senderName = senderNameRaw.trim().ifBlank { "بازیکن" }.take(40)
            val payload = mapOf(
                "roomId" to room.roomCode,
                "senderUid" to uid,
                "senderName" to senderName,
                "senderSeat" to senderSeat.coerceIn(0, 3),
                "memberUids" to room.memberUids,
                "audioBase64" to audioBase64,
                "durationMs" to clampedDuration,
                "sampleRate" to 8000,
                "createdAt" to FieldValue.serverTimestamp()
            )
            packetRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(6) {
            repeat(6) {
                append(chars[Random.nextInt(chars.length)])
            }
        }
    }
}
