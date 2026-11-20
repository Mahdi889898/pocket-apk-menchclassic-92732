package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.GameRoom
import com.example.data.RoomStatus
import com.example.data.UiState
import com.example.ui.theme.BrassGold
import com.example.ui.theme.BrassGoldDark
import com.example.ui.theme.BrassGoldLight
import com.example.ui.theme.CreamText
import com.example.ui.theme.MutedSandText
import com.example.ui.theme.PawnBlue
import com.example.ui.theme.PawnGreen
import com.example.ui.theme.PawnRed
import com.example.ui.theme.PawnYellow
import com.example.ui.theme.VoiceActiveGreen
import com.example.ui.theme.WalnutCardBorder
import com.example.ui.theme.WalnutDeep
import com.example.ui.theme.WalnutSurface
import com.example.ui.theme.WalnutSurfaceHigh

@Composable
fun LobbyScreen(
    viewModel: MenchViewModel,
    onSignOut: () -> Unit
) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val publicRoomsState by viewModel.publicRoomsState.collectAsStateWithLifecycle()
    val myRoomsState by viewModel.myRoomsState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinCodeDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }

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
                alpha = 0.36f,
                modifier = Modifier.fillMaxSize()
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(innerPadding)
                    .align(Alignment.TopCenter)
                    .widthIn(max = 600.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Vintage Hero Header + Profile Bar
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.5.dp, BrassGold.copy(alpha = 0.65f)),
                        colors = CardDefaults.cardColors(containerColor = WalnutSurface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_vintage_lobby_hero_1795183295047),
                                    contentDescription = "تالار منچ کلاسیک",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    WalnutDeep.copy(alpha = 0.25f),
                                                    WalnutDeep.copy(alpha = 0.92f)
                                                )
                                            )
                                        )
                                )
                                IconButton(
                                    onClick = onSignOut,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(48.dp)
                                        .testTag("sign_out_button")
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = WalnutDeep.copy(alpha = 0.7f),
                                        border = BorderStroke(1.dp, BrassGold.copy(alpha = 0.5f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Logout,
                                            contentDescription = stringResource(R.string.sign_out),
                                            tint = BrassGoldLight,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.lobby_title),
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = BrassGoldLight
                                    )
                                    Text(
                                        text = "صفحه قدیمی • ۴ نفره آنلاین • مکالمه صوتی زنده",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CreamText.copy(alpha = 0.85f)
                                    )
                                }
                            }

                            // Player Profile & Stats Strip
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { showEditNameDialog = true }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = BrassGoldDark,
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Casino,
                                                contentDescription = null,
                                                tint = BrassGoldLight,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = profile?.displayName ?: "بازیکن منچ",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = CreamText,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "ویرایش نام",
                                                tint = BrassGold,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            text = "برای تغییر نام لمس کنید",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MutedSandText
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatChip(
                                        icon = Icons.Default.EmojiEvents,
                                        value = "${profile?.wins ?: 0}",
                                        label = stringResource(R.string.wins_label)
                                    )
                                    StatChip(
                                        icon = Icons.Default.People,
                                        value = "${profile?.gamesPlayed ?: 0}",
                                        label = stringResource(R.string.games_label)
                                    )
                                }
                            }
                        }
                    }
                }

                // Primary Actions: Create Room & Join by Code
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { showCreateDialog = true },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrassGold,
                                contentColor = WalnutDeep
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("create_room_button")
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.create_room),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        OutlinedButton(
                            onClick = { showJoinCodeDialog = true },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.5.dp, BrassGold),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = WalnutSurface.copy(alpha = 0.85f),
                                contentColor = BrassGoldLight
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("join_code_button")
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.join_by_code),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                // My Active Rooms
                val myRooms = (myRoomsState as? UiState.Success)?.data
                    ?.filter { it.roomStatusEnum() != RoomStatus.FINISHED }
                    .orEmpty()

                if (myRooms.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.my_rooms_section),
                            style = MaterialTheme.typography.titleMedium,
                            color = BrassGoldLight,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(myRooms, key = { "my_${it.roomCode}" }) { room ->
                        RoomCard(
                            room = room,
                            isMember = true,
                            onAction = { viewModel.openExistingRoom(room.roomCode) }
                        )
                    }
                }

                // Public Waiting Rooms
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.open_rooms_section),
                            style = MaterialTheme.typography.titleMedium,
                            color = BrassGoldLight
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = VoiceActiveGreen.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, VoiceActiveGreen.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = VoiceActiveGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "چت صوتی فعال",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VoiceActiveGreen
                                )
                            }
                        }
                    }
                }

                when (val state = publicRoomsState) {
                    is UiState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = BrassGold)
                            }
                        }
                    }
                    is UiState.Error -> {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = WalnutSurface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                    is UiState.Success -> {
                        val openRooms = state.data.filterNot { viewModel.currentUserId in it.memberUids }
                        if (openRooms.isEmpty()) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = WalnutSurface.copy(alpha = 0.85f)),
                                    border = BorderStroke(1.dp, WalnutCardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Casino,
                                            contentDescription = null,
                                            tint = BrassGold.copy(alpha = 0.7f),
                                            modifier = Modifier.size(38.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = stringResource(R.string.no_open_rooms),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MutedSandText
                                        )
                                    }
                                }
                            }
                        } else {
                            items(openRooms, key = { "pub_${it.roomCode}" }) { room ->
                                RoomCard(
                                    room = room,
                                    isMember = false,
                                    onAction = { viewModel.joinRoom(room.roomCode) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateRoomDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, isPublic ->
                showCreateDialog = false
                viewModel.createRoom(name, isPublic)
            }
        )
    }

    if (showJoinCodeDialog) {
        JoinByCodeDialog(
            onDismiss = { showJoinCodeDialog = false },
            onJoin = { code ->
                showJoinCodeDialog = false
                viewModel.joinRoom(code)
            }
        )
    }

    if (showEditNameDialog) {
        EditDisplayNameDialog(
            currentName = profile?.displayName ?: "",
            onDismiss = { showEditNameDialog = false },
            onSave = { newName ->
                showEditNameDialog = false
                viewModel.updateDisplayName(newName)
            }
        )
    }
}

@Composable
private fun StatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = WalnutSurfaceHigh,
        border = BorderStroke(1.dp, WalnutCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BrassGold,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "$value $label",
                style = MaterialTheme.typography.labelMedium,
                color = CreamText
            )
        }
    }
}

@Composable
private fun RoomCard(
    room: GameRoom,
    isMember: Boolean,
    onAction: () -> Unit
) {
    val seatUids = room.normalizedSeatUids()
    val seatColors = listOf(PawnRed, PawnBlue, PawnYellow, PawnGreen)
    val statusText = when (room.roomStatusEnum()) {
        RoomStatus.WAITING -> "در انتظار بازیکنان (${room.memberUids.size}/۴)"
        RoomStatus.PLAYING -> "در حال بازی"
        RoomStatus.FINISHED -> "پایان یافته"
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = WalnutSurface.copy(alpha = 0.94f)),
        border = BorderStroke(1.dp, if (isMember) BrassGold.copy(alpha = 0.6f) else WalnutCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = room.roomName.ifBlank { "میز منچ کلاسیک" },
                        style = MaterialTheme.typography.titleMedium,
                        color = CreamText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = BrassGoldDark.copy(alpha = 0.45f)
                    ) {
                        Text(
                            text = room.roomCode,
                            style = MaterialTheme.typography.labelSmall,
                            color = BrassGoldLight,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    seatColors.forEachIndexed { idx, color ->
                        val occupied = seatUids.getOrElse(idx) { "" }.isNotBlank()
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (occupied) color else color.copy(alpha = 0.2f))
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSandText
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Button(
                onClick = onAction,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMember) VoiceActiveGreen else BrassGold,
                    contentColor = WalnutDeep
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("room_action_${room.roomCode}")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isMember) stringResource(R.string.rejoin_button) else stringResource(R.string.join_button),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun CreateRoomDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Boolean) -> Unit
) {
    var roomName by remember { mutableStateOf("دورهمی منچ قدیمی") }
    var publicLobby by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WalnutSurface,
        titleContentColor = BrassGoldLight,
        textContentColor = CreamText,
        title = {
            Text(
                text = stringResource(R.string.create_room),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { if (it.length <= 40) roomName = it },
                    label = { Text(stringResource(R.string.room_name_hint)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrassGold,
                        unfocusedBorderColor = WalnutCardBorder,
                        focusedLabelColor = BrassGoldLight,
                        cursorColor = BrassGold
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("room_name_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.public_room_toggle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CreamText,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = publicLobby,
                        onCheckedChange = { publicLobby = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = WalnutDeep,
                            checkedTrackColor = BrassGold
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(roomName, publicLobby) },
                colors = ButtonDefaults.buttonColors(containerColor = BrassGold, contentColor = WalnutDeep),
                modifier = Modifier.testTag("confirm_create_room_button")
            ) {
                Text(stringResource(R.string.create_button), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button), color = MutedSandText)
            }
        }
    )
}

@Composable
private fun JoinByCodeDialog(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WalnutSurface,
        titleContentColor = BrassGoldLight,
        textContentColor = CreamText,
        title = {
            Text(
                text = stringResource(R.string.join_by_code),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { raw ->
                    code = raw.uppercase().filter { it.isLetterOrDigit() }.take(12)
                },
                label = { Text(stringResource(R.string.room_code_hint)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrassGold,
                    unfocusedBorderColor = WalnutCardBorder,
                    focusedLabelColor = BrassGoldLight,
                    cursorColor = BrassGold
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("join_room_code_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onJoin(code) },
                enabled = code.length >= 4,
                colors = ButtonDefaults.buttonColors(containerColor = BrassGold, contentColor = WalnutDeep),
                modifier = Modifier.testTag("confirm_join_code_button")
            ) {
                Text(stringResource(R.string.join_button), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button), color = MutedSandText)
            }
        }
    )
}

@Composable
private fun EditDisplayNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember(currentName) { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WalnutSurface,
        titleContentColor = BrassGoldLight,
        textContentColor = CreamText,
        title = {
            Text(text = "نام نمایشی بازیکن", style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 40) name = it },
                label = { Text("نام شما روی میز منچ") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrassGold,
                    unfocusedBorderColor = WalnutCardBorder,
                    focusedLabelColor = BrassGoldLight,
                    cursorColor = BrassGold
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(name) },
                enabled = name.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = BrassGold, contentColor = WalnutDeep)
            ) {
                Text("ذخیره", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button), color = MutedSandText)
            }
        }
    )
}
