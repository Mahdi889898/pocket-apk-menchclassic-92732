package com.example.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.GameRoom
import com.example.data.RoomStatus
import com.example.data.SeatColor
import com.example.data.TurnPhase
import com.example.data.UiState
import com.example.game.MenchRulesEngine
import com.example.ui.theme.BrassGold
import com.example.ui.theme.BrassGoldDark
import com.example.ui.theme.BrassGoldLight
import com.example.ui.theme.CreamText
import com.example.ui.theme.MutedSandText
import com.example.ui.theme.VoiceActiveGreen
import com.example.ui.theme.VoiceRecordingRed
import com.example.ui.theme.WalnutCardBorder
import com.example.ui.theme.WalnutDeep
import com.example.ui.theme.WalnutSurface
import com.example.ui.theme.WalnutSurfaceHigh
import kotlinx.coroutines.launch

@Composable
fun GameRoomScreen(
    viewModel: MenchViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val roomState by viewModel.activeRoomState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val isRollingDice by viewModel.isRollingDice.collectAsStateWithLifecycle()
    val rollingPreviewFace by viewModel.rollingPreviewFace.collectAsStateWithLifecycle()

    val isOpenMicEnabled by viewModel.isOpenMicEnabled.collectAsStateWithLifecycle()
    val isPushToTalkActive by viewModel.isPushToTalkActive.collectAsStateWithLifecycle()
    val isSpeakerMuted by viewModel.isSpeakerMuted.collectAsStateWithLifecycle()
    val micAmplitude by viewModel.micAmplitude.collectAsStateWithLifecycle()
    val speakingSeats by viewModel.speakingSeats.collectAsStateWithLifecycle()
    val voiceStatusText by viewModel.voiceStatusText.collectAsStateWithLifecycle()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar("دسترسی میکروفون برای مکالمه صوتی تایید نشد.")
            }
        }
    }

    BackHandler {
        viewModel.exitRoom(leaveWaitingIfApplicable = false)
    }

    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearBanner()
    }

    Scaffold(
        containerColor = WalnutDeep,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.img_vintage_wood_backdrop_1795183354498),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.55f,
                modifier = Modifier.fillMaxSize()
            )

            when (val state = roomState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BrassGold)
                    }
                }
                is UiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.exitRoom() },
                            colors = ButtonDefaults.buttonColors(containerColor = BrassGold, contentColor = WalnutDeep)
                        ) {
                            Text(stringResource(R.string.back_to_lobby))
                        }
                    }
                }
                is UiState.Success -> {
                    val room = state.data
                    if (room == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "این اتاق بسته شده یا وجود ندارد.",
                                style = MaterialTheme.typography.titleMedium,
                                color = CreamText
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.exitRoom() },
                                colors = ButtonDefaults.buttonColors(containerColor = BrassGold, contentColor = WalnutDeep)
                            ) {
                                Text(stringResource(R.string.back_to_lobby))
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                                .padding(innerPadding)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .align(Alignment.TopCenter)
                                .widthIn(max = 580.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Top Bar: Back, Room Title/Code, Speaker Toggle
                            RoomTopBar(
                                room = room,
                                isSpeakerMuted = isSpeakerMuted,
                                onBack = { viewModel.exitRoom(leaveWaitingIfApplicable = false) },
                                onCopyCode = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("RoomCode", room.roomCode))
                                    scope.launch {
                                        snackbarHostState.showSnackbar("کد اتاق ${room.roomCode} کپی شد!")
                                    }
                                },
                                onToggleSpeaker = { viewModel.toggleSpeakerMute() }
                            )

                            when (room.roomStatusEnum()) {
                                RoomStatus.WAITING -> {
                                    WaitingRoomContent(
                                        room = room,
                                        isHost = room.hostUid == viewModel.currentUserId,
                                        isBusy = isBusy,
                                        speakingSeats = speakingSeats,
                                        onStartMatch = { viewModel.startMatch(room) },
                                        onLeaveWaiting = { viewModel.exitRoom(leaveWaitingIfApplicable = true) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                RoomStatus.PLAYING, RoomStatus.FINISHED -> {
                                    ActiveGameContent(
                                        room = room,
                                        myUid = viewModel.currentUserId,
                                        isRollingDice = isRollingDice,
                                        rollingPreviewFace = rollingPreviewFace,
                                        speakingSeats = speakingSeats,
                                        onRollDice = { viewModel.rollDice(room) },
                                        onTokenTap = { tokenIdx -> viewModel.moveToken(room, tokenIdx) },
                                        onBackToLobby = { viewModel.exitRoom() },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Persistent In-Game Voice Chat Console Bar (always accessible on the game screen!)
                            VoiceChatDock(
                                room = room,
                                hasMicPermission = hasMicPermission,
                                isOpenMicEnabled = isOpenMicEnabled,
                                isPushToTalkActive = isPushToTalkActive,
                                isSpeakerMuted = isSpeakerMuted,
                                micAmplitude = micAmplitude,
                                voiceStatusText = voiceStatusText,
                                onRequestMicPermission = {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onToggleOpenMic = { viewModel.toggleOpenMic(room) },
                                onStartPtt = { viewModel.startPushToTalk(room) },
                                onReleasePtt = { viewModel.releasePushToTalk(room) },
                                onToggleSpeaker = { viewModel.toggleSpeakerMute() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomTopBar(
    room: GameRoom,
    isSpeakerMuted: Boolean,
    onBack: () -> Unit,
    onCopyCode: () -> Unit,
    onToggleSpeaker: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .testTag("game_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back_to_lobby),
                tint = BrassGoldLight
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = room.roomName.ifBlank { "میز منچ کلاسیک" },
                style = MaterialTheme.typography.titleMedium,
                color = CreamText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Surface(
                onClick = onCopyCode,
                shape = RoundedCornerShape(8.dp),
                color = WalnutSurfaceHigh,
                border = BorderStroke(1.dp, BrassGold.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "کد اتاق: ${room.roomCode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrassGoldLight
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.copy_code),
                        tint = BrassGold,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        IconButton(
            onClick = onToggleSpeaker,
            modifier = Modifier
                .size(48.dp)
                .testTag("top_speaker_toggle")
        ) {
            Icon(
                imageVector = if (isSpeakerMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = if (isSpeakerMuted) stringResource(R.string.voice_speaker_muted)
                else stringResource(R.string.voice_speaker_on),
                tint = if (isSpeakerMuted) MutedSandText else VoiceActiveGreen
            )
        }
    }
}

@Composable
private fun WaitingRoomContent(
    room: GameRoom,
    isHost: Boolean,
    isBusy: Boolean,
    speakingSeats: Set<Int>,
    onStartMatch: () -> Unit,
    onLeaveWaiting: () -> Unit,
    modifier: Modifier = Modifier
) {
    val seatUids = room.normalizedSeatUids()
    val seatNames = room.normalizedSeatNames()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.5.dp, BrassGold.copy(alpha = 0.7f)),
            colors = CardDefaults.cardColors(containerColor = WalnutSurface.copy(alpha = 0.94f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.waiting_room_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = BrassGoldLight
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "کد ${room.roomCode} را برای دوستان بفرستید تا به این میز ۴ نفره بپیوندند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedSandText,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 4 Seat Slots Grid (2x2)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WaitingSeatCard(
                            seat = 0,
                            name = seatNames[0],
                            occupied = seatUids[0].isNotBlank(),
                            isSpeaking = 0 in speakingSeats,
                            modifier = Modifier.weight(1f)
                        )
                        WaitingSeatCard(
                            seat = 1,
                            name = seatNames[1],
                            occupied = seatUids[1].isNotBlank(),
                            isSpeaking = 1 in speakingSeats,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WaitingSeatCard(
                            seat = 2,
                            name = seatNames[2],
                            occupied = seatUids[2].isNotBlank(),
                            isSpeaking = 2 in speakingSeats,
                            modifier = Modifier.weight(1f)
                        )
                        WaitingSeatCard(
                            seat = 3,
                            name = seatNames[3],
                            occupied = seatUids[3].isNotBlank(),
                            isSpeaking = 3 in speakingSeats,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                if (isHost) {
                    Button(
                        onClick = onStartMatch,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrassGold,
                            contentColor = WalnutDeep
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("start_match_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.start_match_button),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = WalnutSurfaceHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = BrassGold,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.waiting_for_host),
                                style = MaterialTheme.typography.bodyMedium,
                                color = CreamText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.TextButton(
                    onClick = onLeaveWaiting,
                    modifier = Modifier.testTag("leave_waiting_room_button")
                ) {
                    Text(
                        text = stringResource(R.string.leave_room),
                        color = MutedSandText
                    )
                }
            }
        }
    }
}

@Composable
private fun WaitingSeatCard(
    seat: Int,
    name: String,
    occupied: Boolean,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    val seatColor = seatColorMain(seat)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = WalnutSurfaceHigh,
        border = BorderStroke(
            width = if (isSpeaking) 2.dp else 1.dp,
            color = if (isSpeaking) VoiceActiveGreen else seatColor.copy(alpha = if (occupied) 0.85f else 0.3f)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (occupied) seatColor else seatColor.copy(alpha = 0.25f))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = SeatColor.fromIndex(seat).persianName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedSandText
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (occupied) CreamText else MutedSandText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isSpeaking) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "در حال صحبت",
                    tint = VoiceActiveGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ActiveGameContent(
    room: GameRoom,
    myUid: String,
    isRollingDice: Boolean,
    rollingPreviewFace: Int,
    speakingSeats: Set<Int>,
    onRollDice: () -> Unit,
    onTokenTap: (Int) -> Unit,
    onBackToLobby: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mySeat = room.seatOf(myUid)
    val tokens = room.normalizedTokens()
    val seatNames = room.normalizedSeatNames()
    val botFlags = room.normalizedBotFlags()

    val isMyTurn = room.roomStatusEnum() == RoomStatus.PLAYING && mySeat == room.currentTurnSeat
    val canRollNow = isMyTurn && room.turnPhaseEnum() == TurnPhase.ROLL && !isRollingDice
    val movableTokens = if (isMyTurn && room.turnPhaseEnum() == TurnPhase.MOVE) {
        MenchRulesEngine.getMovableTokenIndices(tokens, mySeat, room.diceValue)
    } else {
        emptyList()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Row of Players matching Top Corners: Yellow (Top-Right in LTR -> first in RTL) & Blue (Top-Left)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SeatBadgeCard(
                seat = 2,
                name = seatNames[2],
                isBot = botFlags[2],
                isTurn = room.currentTurnSeat == 2 && room.roomStatusEnum() == RoomStatus.PLAYING,
                isSpeaking = 2 in speakingSeats,
                finishedCount = countFinishedPawns(tokens, 2),
                isMe = mySeat == 2,
                modifier = Modifier.weight(1f)
            )
            SeatBadgeCard(
                seat = 1,
                name = seatNames[1],
                isBot = botFlags[1],
                isTurn = room.currentTurnSeat == 1 && room.roomStatusEnum() == RoomStatus.PLAYING,
                isSpeaking = 1 in speakingSeats,
                finishedCount = countFinishedPawns(tokens, 1),
                isMe = mySeat == 1,
                modifier = Modifier.weight(1f)
            )
        }

        // Centerpiece Classic Vintage Persian Mensch Board
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            ClassicMenchBoard(
                tokens = tokens,
                currentTurnSeat = room.currentTurnSeat,
                mySeat = mySeat,
                movableTokenIndices = movableTokens,
                onTokenTap = onTokenTap,
                modifier = Modifier.fillMaxWidth(0.98f)
            )

            // Victory Overlay Card when Match Finishes
            if (room.roomStatusEnum() == RoomStatus.FINISHED) {
                val winnerName = seatNames.getOrElse(room.winnerSeat) { "برنده" }
                Card(
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(2.dp, BrassGoldLight),
                    colors = CardDefaults.cardColors(containerColor = WalnutDeep.copy(alpha = 0.94f)),
                    modifier = Modifier.padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = BrassGoldLight,
                            modifier = Modifier.size(46.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.victory_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = BrassGoldLight
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$winnerName (${SeatColor.fromIndex(room.winnerSeat).persianName}) برنده شد!",
                            style = MaterialTheme.typography.titleMedium,
                            color = CreamText,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onBackToLobby,
                            colors = ButtonDefaults.buttonColors(containerColor = BrassGold, contentColor = WalnutDeep)
                        ) {
                            Text(stringResource(R.string.back_to_lobby), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Bottom Row of Players matching Bottom Corners: Green (Bottom-Right -> first in RTL) & Red (Bottom-Left)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SeatBadgeCard(
                seat = 3,
                name = seatNames[3],
                isBot = botFlags[3],
                isTurn = room.currentTurnSeat == 3 && room.roomStatusEnum() == RoomStatus.PLAYING,
                isSpeaking = 3 in speakingSeats,
                finishedCount = countFinishedPawns(tokens, 3),
                isMe = mySeat == 3,
                modifier = Modifier.weight(1f)
            )
            SeatBadgeCard(
                seat = 0,
                name = seatNames[0],
                isBot = botFlags[0],
                isTurn = room.currentTurnSeat == 0 && room.roomStatusEnum() == RoomStatus.PLAYING,
                isSpeaking = 0 in speakingSeats,
                finishedCount = countFinishedPawns(tokens, 0),
                isMe = mySeat == 0,
                modifier = Modifier.weight(1f)
            )
        }

        // Tactile Dice & Turn Event Strip
        if (room.roomStatusEnum() == RoomStatus.PLAYING) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = WalnutSurface.copy(alpha = 0.92f),
                border = BorderStroke(
                    width = if (isMyTurn) 1.5.dp else 1.dp,
                    color = if (isMyTurn) BrassGold else WalnutCardBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    VintageBoneDice(
                        faceValue = if (isRollingDice) rollingPreviewFace else room.diceValue.coerceAtLeast(1),
                        isRolling = isRollingDice,
                        isInteractive = canRollNow,
                        onRollClick = onRollDice
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        val turnTitle = when {
                            canRollNow -> "نوبت شماست — روی تاس بزنید!"
                            isMyTurn && room.turnPhaseEnum() == TurnPhase.MOVE -> "تاس ${room.diceValue} آمد — مهره درخشان را لمس کنید"
                            else -> "نوبت ${seatNames.getOrElse(room.currentTurnSeat) { "بازیکن" }}"
                        }
                        Text(
                            text = turnTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isMyTurn) BrassGoldLight else CreamText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = room.lastEventText.ifBlank { "برای شروع تاس بریزید" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedSandText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (canRollNow) {
                        Button(
                            onClick = onRollDice,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrassGold,
                                contentColor = WalnutDeep
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("roll_dice_text_button")
                        ) {
                            Text(
                                text = stringResource(R.string.roll_dice),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeatBadgeCard(
    seat: Int,
    name: String,
    isBot: Boolean,
    isTurn: Boolean,
    isSpeaking: Boolean,
    finishedCount: Int,
    isMe: Boolean,
    modifier: Modifier = Modifier
) {
    val seatColor = seatColorMain(seat)
    val borderColor by animateColorAsState(
        targetValue = when {
            isSpeaking -> VoiceActiveGreen
            isTurn -> BrassGoldLight
            else -> WalnutCardBorder
        },
        label = "seat_border"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isTurn) WalnutSurfaceHigh else WalnutSurface.copy(alpha = 0.88f),
        border = BorderStroke(if (isTurn || isSpeaking) 1.8.dp else 1.dp, borderColor),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(seatColor)
                    .border(
                        width = if (isSpeaking) 2.dp else 1.dp,
                        color = if (isSpeaking) VoiceActiveGreen else Color.White.copy(alpha = 0.6f),
                        shape = CircleShape
                    )
            ) {
                if (isSpeaking) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "در حال صحبت",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                } else if (isBot) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "ربات",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isMe) "$name (شما)" else name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isTurn) BrassGoldLight else CreamText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    repeat(4) { i ->
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i < finishedCount) BrassGoldLight
                                    else seatColor.copy(alpha = 0.35f)
                                )
                        )
                    }
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "$finishedCount/۴",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedSandText
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceChatDock(
    room: GameRoom,
    hasMicPermission: Boolean,
    isOpenMicEnabled: Boolean,
    isPushToTalkActive: Boolean,
    isSpeakerMuted: Boolean,
    micAmplitude: Float,
    voiceStatusText: String?,
    onRequestMicPermission: () -> Unit,
    onToggleOpenMic: () -> Unit,
    onStartPtt: () -> Unit,
    onReleasePtt: () -> Unit,
    onToggleSpeaker: () -> Unit
) {
    val animatedAmp by animateFloatAsState(targetValue = micAmplitude, label = "voice_amp")

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = WalnutSurface.copy(alpha = 0.96f),
        border = BorderStroke(
            width = 1.2.dp,
            color = when {
                isPushToTalkActive -> VoiceRecordingRed
                isOpenMicEnabled -> VoiceActiveGreen
                else -> BrassGold.copy(alpha = 0.45f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .testTag("voice_chat_dock")
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (!voiceStatusText.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = if (isPushToTalkActive) VoiceRecordingRed else VoiceActiveGreen,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = voiceStatusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = BrassGoldLight
                    )
                }
            }

            if (!hasMicPermission) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.mic_permission_needed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSandText,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onRequestMicPermission,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VoiceActiveGreen,
                            contentColor = WalnutDeep
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("grant_mic_button")
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.grant_mic), style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Open-Mic Live Voice Toggle
                    Surface(
                        onClick = onToggleOpenMic,
                        shape = RoundedCornerShape(14.dp),
                        color = if (isOpenMicEnabled) VoiceActiveGreen else WalnutSurfaceHigh,
                        border = BorderStroke(
                            1.dp,
                            if (isOpenMicEnabled) VoiceActiveGreen else WalnutCardBorder
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("open_mic_toggle_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isOpenMicEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = if (isOpenMicEnabled) stringResource(R.string.voice_live_on)
                                else stringResource(R.string.voice_live_off),
                                tint = if (isOpenMicEnabled) WalnutDeep else BrassGoldLight,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isOpenMicEnabled) "میکروفون باز" else "میکروفون زنده",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isOpenMicEnabled) WalnutDeep else CreamText
                            )
                        }
                    }

                    // Push-to-Talk Hold Pill with Live Equalizer Bars
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isPushToTalkActive) VoiceRecordingRed
                                else BrassGoldDark.copy(alpha = 0.55f)
                            )
                            .border(
                                1.dp,
                                if (isPushToTalkActive) Color.White.copy(alpha = 0.7f) else BrassGold,
                                RoundedCornerShape(14.dp)
                            )
                            .pointerInput(room.roomCode) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    onStartPtt()
                                    waitForUpOrCancellation()
                                    onReleasePtt()
                                }
                            }
                            .testTag("push_to_talk_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            if (isPushToTalkActive || (isOpenMicEnabled && animatedAmp > 0.05f)) {
                                MiniWaveformBars(amplitude = animatedAmp)
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = BrassGoldLight,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = if (isPushToTalkActive) stringResource(R.string.voice_ptt_recording)
                                else stringResource(R.string.voice_ptt_hold),
                                style = MaterialTheme.typography.labelMedium,
                                color = CreamText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Speaker Mute Toggle
                    Surface(
                        onClick = onToggleSpeaker,
                        shape = RoundedCornerShape(14.dp),
                        color = WalnutSurfaceHigh,
                        border = BorderStroke(
                            1.dp,
                            if (isSpeakerMuted) WalnutCardBorder else VoiceActiveGreen.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("dock_speaker_toggle")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isSpeakerMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = if (isSpeakerMuted) stringResource(R.string.voice_speaker_muted)
                                else stringResource(R.string.voice_speaker_on),
                                tint = if (isSpeakerMuted) MutedSandText else VoiceActiveGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniWaveformBars(amplitude: Float) {
    val heights = listOf(0.45f, 0.95f, 0.65f, 1.0f, 0.55f)
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        heights.forEach { factor ->
            val barH = (6f + (16f * amplitude.coerceIn(0.15f, 1f) * factor)).dp
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barH)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White)
            )
        }
    }
}

private fun countFinishedPawns(tokens: List<Int>, seat: Int): Int {
    val base = seat * 4
    var count = 0
    for (i in 0 until 4) {
        if (tokens.getOrElse(base + i) { -1 } >= MenchRulesEngine.TRACK_LENGTH) {
            count++
        }
    }
    return count
}
