package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GameRoom
import com.example.data.MenchRepository
import com.example.data.RoomStatus
import com.example.data.TurnPhase
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.data.VoicePacket
import com.example.game.MenchRulesEngine
import com.example.voice.VoiceChatEngine
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class MenchViewModel(
    private val repository: MenchRepository,
    val currentUserId: String,
    initialDisplayName: String
) : ViewModel() {

    private val voiceEngine = VoiceChatEngine(viewModelScope)

    private val _activeRoomCode = MutableStateFlow<String?>(null)
    val activeRoomCode: StateFlow<String?> = _activeRoomCode.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    // Tactile dice rolling animation state
    private val _isRollingDice = MutableStateFlow(false)
    val isRollingDice: StateFlow<Boolean> = _isRollingDice.asStateFlow()

    private val _rollingPreviewFace = MutableStateFlow(6)
    val rollingPreviewFace: StateFlow<Int> = _rollingPreviewFace.asStateFlow()

    // In-game voice chat UI state
    private val _isOpenMicEnabled = MutableStateFlow(false)
    val isOpenMicEnabled: StateFlow<Boolean> = _isOpenMicEnabled.asStateFlow()

    private val _isPushToTalkActive = MutableStateFlow(false)
    val isPushToTalkActive: StateFlow<Boolean> = _isPushToTalkActive.asStateFlow()

    private val _isSpeakerMuted = MutableStateFlow(false)
    val isSpeakerMuted: StateFlow<Boolean> = _isSpeakerMuted.asStateFlow()

    private val _micAmplitude = MutableStateFlow(0f)
    val micAmplitude: StateFlow<Float> = _micAmplitude.asStateFlow()

    private val _speakingSeats = MutableStateFlow<Set<Int>>(emptySet())
    val speakingSeats: StateFlow<Set<Int>> = _speakingSeats.asStateFlow()

    private val _voiceStatusText = MutableStateFlow<String?>(null)
    val voiceStatusText: StateFlow<String?> = _voiceStatusText.asStateFlow()

    private val playedPacketIds = mutableSetOf<String>()
    private var roomEnteredTimestampMs: Long = 0L
    private var voiceObserverJob: Job? = null
    private var botTurnJob: Job? = null
    private var lastBotTriggerKey: String? = null
    private val recordedFinishedRooms = mutableSetOf<String>()

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing user profile", firestoreError ?: error)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    val publicRoomsState: StateFlow<UiState<List<GameRoom>>> = repository.observePublicWaitingRooms()
        .map<List<GameRoom>, UiState<List<GameRoom>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing public rooms", root)
            emit(UiState.Error(root.message ?: "خطا در بارگذاری میزهای عمومی"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val myRoomsState: StateFlow<UiState<List<GameRoom>>> = repository.observeMyRooms(currentUserId)
        .map<List<GameRoom>, UiState<List<GameRoom>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing my rooms", root)
            emit(UiState.Error(root.message ?: "خطا در بارگذاری میزهای شما"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val activeRoomState: StateFlow<UiState<GameRoom?>> = _activeRoomCode
        .flatMapLatest { code ->
            if (code == null) {
                flowOf(UiState.Success(null))
            } else {
                repository.observeRoom(code)
                    .map<GameRoom?, UiState<GameRoom?>> { room ->
                        if (room != null) {
                            handleRoomSideEffects(room)
                        }
                        UiState.Success(room)
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val root = firestoreError ?: error
                        Log.w(TAG, "Error observing active room $code", root)
                        emit(UiState.Error(root.message ?: "خطا در همگام‌سازی بازی"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(initialDisplayName)
        }
    }

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun updateDisplayName(newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val res = repository.updateDisplayName(clean)
            if (res.isSuccess) {
                _bannerMessage.value = "نام بازیکن به «$clean» تغییر یافت."
            } else {
                _bannerMessage.value = res.exceptionOrNull()?.localizedMessage ?: "خطا در ذخیره نام"
            }
        }
    }

    fun createRoom(roomName: String, publicLobby: Boolean) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val myName = userProfile.value?.displayName?.ifBlank { "بازیکن منچ" } ?: "بازیکن منچ"
            val result = repository.createRoom(roomName, publicLobby, myName)
            _isBusy.value = false
            result.onSuccess { code ->
                enterRoom(code)
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "خطا در ساخت میز جدید"
            }
        }
    }

    fun joinRoom(roomCode: String) {
        if (_isBusy.value) return
        val cleanCode = roomCode.trim().uppercase()
        if (cleanCode.length < 4) {
            _bannerMessage.value = "لطفاً کد معتبر اتاق را وارد کنید."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val myName = userProfile.value?.displayName?.ifBlank { "بازیکن منچ" } ?: "بازیکن منچ"
            val result = repository.joinRoom(cleanCode, myName)
            _isBusy.value = false
            result.onSuccess { code ->
                enterRoom(code)
            }.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "ورود به اتاق ناموفق بود"
            }
        }
    }

    fun openExistingRoom(roomCode: String) {
        enterRoom(roomCode)
    }

    private fun enterRoom(roomCode: String) {
        stopVoiceCapture()
        playedPacketIds.clear()
        roomEnteredTimestampMs = System.currentTimeMillis() - 1500L
        lastBotTriggerKey = null
        _activeRoomCode.value = roomCode
        startObservingVoice(roomCode)
    }

    fun exitRoom(leaveWaitingIfApplicable: Boolean = false) {
        val currentRoom = (activeRoomState.value as? UiState.Success)?.data
        stopVoiceCapture()
        voiceObserverJob?.cancel()
        voiceObserverJob = null
        botTurnJob?.cancel()
        botTurnJob = null
        _speakingSeats.value = emptySet()
        _voiceStatusText.value = null
        _activeRoomCode.value = null

        if (leaveWaitingIfApplicable && currentRoom != null && currentRoom.roomStatusEnum() == RoomStatus.WAITING) {
            viewModelScope.launch {
                repository.leaveWaitingRoom(currentRoom)
            }
        }
    }

    fun startMatch(room: GameRoom) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.startMatch(room)
            _isBusy.value = false
            res.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "شروع بازی ناموفق بود"
            }
        }
    }

    fun rollDice(room: GameRoom) {
        if (_isRollingDice.value) return
        if (room.roomStatusEnum() != RoomStatus.PLAYING) return
        if (room.turnPhaseEnum() != TurnPhase.ROLL) return
        val mySeat = room.seatOf(currentUserId)
        if (mySeat != room.currentTurnSeat) return

        viewModelScope.launch {
            _isRollingDice.value = true
            voiceEngine.playDiceRollSound()
            val finalFace = Random.nextInt(1, 7)
            repeat(8) {
                _rollingPreviewFace.value = Random.nextInt(1, 7)
                delay(55L)
            }
            _rollingPreviewFace.value = finalFace
            delay(120L)
            val result = repository.submitDiceRoll(room, finalFace)
            _isRollingDice.value = false
            result.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "خطا در ثبت پرتاب تاس"
            }
        }
    }

    fun moveToken(room: GameRoom, tokenIndex: Int) {
        if (_isBusy.value || _isRollingDice.value) return
        if (room.roomStatusEnum() != RoomStatus.PLAYING) return
        if (room.turnPhaseEnum() != TurnPhase.MOVE) return
        val mySeat = room.seatOf(currentUserId)
        if (mySeat != room.currentTurnSeat) return

        val movable = MenchRulesEngine.getMovableTokenIndices(
            room.normalizedTokens(),
            mySeat,
            room.diceValue
        )
        if (tokenIndex !in movable) return

        viewModelScope.launch {
            _isBusy.value = true
            val outcomePreview = MenchRulesEngine.applyMove(room, mySeat, tokenIndex, room.diceValue)
            voiceEngine.playPawnStepSound(isCapture = outcomePreview.capturedSeat != null)
            val result = repository.submitTokenMove(room, tokenIndex)
            _isBusy.value = false
            result.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "خطا در حرکت مهره"
            }
        }
    }

    // --- VOICE CHAT CONTROLS ---

    fun toggleSpeakerMute() {
        val next = !_isSpeakerMuted.value
        _isSpeakerMuted.value = next
        voiceEngine.speakerMuted = next
    }

    fun toggleOpenMic(room: GameRoom) {
        if (_isOpenMicEnabled.value) {
            stopVoiceCapture()
            _voiceStatusText.value = null
            return
        }
        val mySeat = room.seatOf(currentUserId)
        if (mySeat < 0) return

        _isOpenMicEnabled.value = true
        _isPushToTalkActive.value = false
        _voiceStatusText.value = "میکروفون زنده فعال است..."
        voiceEngine.startOpenMic(
            onAmplitude = { amp -> _micAmplitude.value = amp },
            onVoiceChunkReady = { b64, durationMs ->
                val latestRoom = (activeRoomState.value as? UiState.Success)?.data ?: room
                val senderName = latestRoom.normalizedSeatNames().getOrElse(mySeat) {
                    userProfile.value?.displayName ?: "بازیکن"
                }
                viewModelScope.launch {
                    pulseSeatSpeaking(mySeat, durationMs.toLong())
                    repository.sendVoicePacket(latestRoom, mySeat, senderName, b64, durationMs)
                }
            }
        )
    }

    fun startPushToTalk(room: GameRoom) {
        val mySeat = room.seatOf(currentUserId)
        if (mySeat < 0) return
        _isOpenMicEnabled.value = false
        _isPushToTalkActive.value = true
        _voiceStatusText.value = "در حال ضبط صدا..."
        voiceEngine.startPushToTalk { amp ->
            _micAmplitude.value = amp
        }
    }

    fun releasePushToTalk(room: GameRoom) {
        if (!_isPushToTalkActive.value) return
        _isPushToTalkActive.value = false
        _micAmplitude.value = 0f
        val encoded = voiceEngine.stopPushToTalkAndEncode()
        if (encoded == null) {
            _voiceStatusText.value = null
            return
        }
        val (base64, durationMs) = encoded
        val mySeat = room.seatOf(currentUserId)
        if (mySeat < 0) return
        val senderName = room.normalizedSeatNames().getOrElse(mySeat) {
            userProfile.value?.displayName ?: "بازیکن"
        }
        _voiceStatusText.value = "صدا ارسال شد"
        viewModelScope.launch {
            pulseSeatSpeaking(mySeat, durationMs.toLong())
            val res = repository.sendVoicePacket(room, mySeat, senderName, base64, durationMs)
            if (res.isFailure) {
                _bannerMessage.value = "ارسال پیام صوتی ناموفق بود."
            }
            delay(1400L)
            if (!_isOpenMicEnabled.value && !_isPushToTalkActive.value) {
                _voiceStatusText.value = null
            }
        }
    }

    fun stopVoiceCapture() {
        _isOpenMicEnabled.value = false
        _isPushToTalkActive.value = false
        _micAmplitude.value = 0f
        voiceEngine.stopOpenMic()
        voiceEngine.stopPushToTalkAndEncode()
    }

    private fun startObservingVoice(roomCode: String) {
        voiceObserverJob?.cancel()
        voiceObserverJob = viewModelScope.launch {
            repository.observeVoicePackets(roomCode, currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Voice stream error", firestoreError ?: error)
                }
                .collect { packets ->
                    for (packet in packets) {
                        if (packet.id.isBlank() || packet.id in playedPacketIds) continue
                        val packetMs = packet.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                        if (packetMs < roomEnteredTimestampMs) {
                            playedPacketIds.add(packet.id)
                            continue
                        }
                        playedPacketIds.add(packet.id)
                        if (packet.senderUid == currentUserId) continue
                        playIncomingVoicePacket(packet)
                    }
                }
        }
    }

    private fun playIncomingVoicePacket(packet: VoicePacket) {
        viewModelScope.launch {
            _voiceStatusText.value = "${packet.senderName} در حال صحبت است..."
            pulseSeatSpeaking(packet.senderSeat, packet.durationMs.toLong().coerceAtLeast(900L))
            voiceEngine.playVoiceBase64(packet.audioBase64, packet.sampleRate)
            delay(400L)
            if (!_isOpenMicEnabled.value && !_isPushToTalkActive.value) {
                _voiceStatusText.value = null
            }
        }
    }

    private fun pulseSeatSpeaking(seat: Int, durationMs: Long) {
        viewModelScope.launch {
            _speakingSeats.value = _speakingSeats.value + seat
            delay(durationMs.coerceIn(700L, 6500L))
            _speakingSeats.value = _speakingSeats.value - seat
        }
    }

    // --- HOST BOT ORCHESTRATION & MATCH RESULT TRACKING ---

    private fun handleRoomSideEffects(room: GameRoom) {
        // 1. Record stats once when a match finishes
        if (room.roomStatusEnum() == RoomStatus.FINISHED && room.roomCode !in recordedFinishedRooms) {
            recordedFinishedRooms.add(room.roomCode)
            val mySeat = room.seatOf(currentUserId)
            if (mySeat >= 0) {
                viewModelScope.launch {
                    repository.recordMatchResult(won = room.winnerSeat == mySeat)
                }
            }
        }

        // 2. Host executes turns for any AI Bot seats
        if (room.roomStatusEnum() != RoomStatus.PLAYING) return
        if (room.hostUid != currentUserId) return

        val turnSeat = room.currentTurnSeat
        val isBotTurn = room.normalizedBotFlags().getOrElse(turnSeat) { false }
        if (!isBotTurn) return

        val triggerKey = "${room.roomCode}|$turnSeat|${room.turnPhase}|${room.diceValue}|${room.tokens.hashCode()}"
        if (triggerKey == lastBotTriggerKey) return
        lastBotTriggerKey = triggerKey

        botTurnJob?.cancel()
        botTurnJob = viewModelScope.launch {
            when (room.turnPhaseEnum()) {
                TurnPhase.ROLL -> {
                    delay(850L)
                    voiceEngine.playDiceRollSound()
                    val rolled = Random.nextInt(1, 7)
                    repository.submitDiceRoll(room, rolled)
                }
                TurnPhase.MOVE -> {
                    delay(750L)
                    val chosen = MenchRulesEngine.chooseBotTokenIndex(
                        room.normalizedTokens(),
                        turnSeat,
                        room.diceValue
                    )
                    if (chosen != null) {
                        val preview = MenchRulesEngine.applyMove(room, turnSeat, chosen, room.diceValue)
                        voiceEngine.playPawnStepSound(isCapture = preview.capturedSeat != null)
                        repository.submitTokenMove(room, chosen)
                    }
                }
                TurnPhase.FINISHED -> Unit
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine.release()
    }

    private companion object {
        const val TAG = "MenchViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
