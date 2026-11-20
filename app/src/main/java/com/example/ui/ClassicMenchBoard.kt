package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.game.GridCell
import com.example.game.MenchRulesEngine
import com.example.ui.theme.BrassGold
import com.example.ui.theme.BrassGoldDark
import com.example.ui.theme.BrassGoldLight
import com.example.ui.theme.ParchmentAged
import com.example.ui.theme.ParchmentBase
import com.example.ui.theme.ParchmentBorder
import com.example.ui.theme.ParchmentLight
import com.example.ui.theme.PawnBlue
import com.example.ui.theme.PawnBlueDark
import com.example.ui.theme.PawnBlueLight
import com.example.ui.theme.PawnGreen
import com.example.ui.theme.PawnGreenDark
import com.example.ui.theme.PawnGreenLight
import com.example.ui.theme.PawnRed
import com.example.ui.theme.PawnRedDark
import com.example.ui.theme.PawnRedLight
import com.example.ui.theme.PawnYellow
import com.example.ui.theme.PawnYellowDark
import com.example.ui.theme.PawnYellowLight
import com.example.ui.theme.VintageInk
import com.example.ui.theme.VintageTrackFill
import com.example.ui.theme.WalnutDeep
import com.example.ui.theme.WalnutSurface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private data class SeatPalette(
    val main: Color,
    val light: Color,
    val dark: Color
)

fun seatColorMain(seat: Int): Color = when (seat) {
    0 -> PawnRed
    1 -> PawnBlue
    2 -> PawnYellow
    else -> PawnGreen
}

private fun seatPalette(seat: Int): SeatPalette = when (seat) {
    0 -> SeatPalette(PawnRed, PawnRedLight, PawnRedDark)
    1 -> SeatPalette(PawnBlue, PawnBlueLight, PawnBlueDark)
    2 -> SeatPalette(PawnYellow, PawnYellowLight, PawnYellowDark)
    else -> SeatPalette(PawnGreen, PawnGreenLight, PawnGreenDark)
}

@Composable
fun ClassicMenchBoard(
    tokens: List<Int>,
    currentTurnSeat: Int,
    mySeat: Int,
    movableTokenIndices: List<Int>,
    onTokenTap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "board_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pawn_halo_pulse"
    )
    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pawn_bounce"
    )

    // Handcrafted outer walnut wooden bezel around the classic vintage parchment board
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .shadow(14.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF422A19), Color(0xFF23150C), Color(0xFF3A2415))
                )
            )
            .border(2.dp, BrassGold.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
            .padding(8.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, ParchmentBorder, RoundedCornerShape(14.dp))
            .semantics { contentDescription = "صفحه کلاسیک منچ قدیمی" }
            .testTag("classic_mench_board")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(tokens, mySeat, movableTokenIndices) {
                    detectTapGestures { tapOffset ->
                        if (mySeat !in 0..3 || movableTokenIndices.isEmpty()) return@detectTapGestures
                        val pad = size.width * 0.045f
                        val gridSpan = size.width - pad * 2f
                        val cell = gridSpan / MenchRulesEngine.BOARD_SIZE

                        var bestToken: Int? = null
                        var bestDist = cell * 0.92f
                        val base = mySeat * 4
                        for (tokenIdx in movableTokenIndices) {
                            val prog = tokens.getOrElse(base + tokenIdx) { -1 }
                            val gc = MenchRulesEngine.tokenGridCell(mySeat, tokenIdx, prog)
                            val cx = pad + (gc.col + 0.5f) * cell
                            val cy = pad + (gc.row + 0.5f) * cell
                            val dist = hypot(tapOffset.x - cx, tapOffset.y - cy)
                            if (dist < bestDist) {
                                bestDist = dist
                                bestToken = tokenIdx
                            }
                        }
                        if (bestToken != null) {
                            onTokenTap(bestToken)
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val pad = w * 0.045f
            val gridSpan = w - pad * 2f
            val cell = gridSpan / MenchRulesEngine.BOARD_SIZE

            fun cellCenter(gc: GridCell): Offset = Offset(
                x = pad + (gc.col + 0.5f) * cell,
                y = pad + (gc.row + 0.5f) * cell
            )

            // 1. Aged Vintage Cream Parchment Surface
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(ParchmentLight, ParchmentBase, ParchmentAged),
                    center = Offset(w / 2f, h / 2f),
                    radius = w * 0.78f
                )
            )

            // Subtle double vintage ink & gold decorative outer border inside the parchment
            drawRoundRect(
                color = ParchmentBorder.copy(alpha = 0.65f),
                topLeft = Offset(pad * 0.38f, pad * 0.38f),
                size = Size(w - pad * 0.76f, h - pad * 0.76f),
                cornerRadius = CornerRadius(cell * 0.35f, cell * 0.35f),
                style = Stroke(width = 2.2f)
            )
            drawRoundRect(
                color = BrassGoldDark.copy(alpha = 0.35f),
                topLeft = Offset(pad * 0.62f, pad * 0.62f),
                size = Size(w - pad * 1.24f, h - pad * 1.24f),
                cornerRadius = CornerRadius(cell * 0.25f, cell * 0.25f),
                style = Stroke(width = 1.1f)
            )

            // 2. Classic Vintage Corner Home Yards (2x2 colored sockets with antique frames)
            for (seat in 0 until 4) {
                val palette = seatPalette(seat)
                val yardList = MenchRulesEngine.yardCells[seat].orEmpty()
                val minCol = yardList.minOf { it.col }
                val minRow = yardList.minOf { it.row }
                val topLeft = Offset(
                    x = pad + minCol * cell - cell * 0.08f,
                    y = pad + minRow * cell - cell * 0.08f
                )
                val yardSize = cell * 2.16f
                val isTurnCorner = seat == currentTurnSeat

                // Yard background tint
                drawRoundRect(
                    color = palette.main.copy(alpha = if (isTurnCorner) 0.20f else 0.11f),
                    topLeft = topLeft,
                    size = Size(yardSize, yardSize),
                    cornerRadius = CornerRadius(cell * 0.38f, cell * 0.38f)
                )
                // Vintage yard border (glows gold on active seat's turn)
                drawRoundRect(
                    color = if (isTurnCorner) BrassGold else palette.dark.copy(alpha = 0.72f),
                    topLeft = topLeft,
                    size = Size(yardSize, yardSize),
                    cornerRadius = CornerRadius(cell * 0.38f, cell * 0.38f),
                    style = Stroke(width = if (isTurnCorner) 3.6f else 2.0f)
                )

                // 4 Circular colored home-yard sockets
                for (gc in yardList) {
                    val c = cellCenter(gc)
                    drawCircle(
                        color = palette.main.copy(alpha = 0.85f),
                        radius = cell * 0.34f,
                        center = c
                    )
                    drawCircle(
                        color = VintageTrackFill.copy(alpha = 0.45f),
                        radius = cell * 0.26f,
                        center = c,
                        style = Stroke(width = 1.6f)
                    )
                    drawCircle(
                        color = VintageInk,
                        radius = cell * 0.34f,
                        center = c,
                        style = Stroke(width = 1.8f)
                    )
                }
            }

            // 3. Connecting Sepia Ink Path around all 40 outer cells
            val trackPath = Path()
            MenchRulesEngine.mainTrackCells.forEachIndexed { idx, gc ->
                val p = cellCenter(gc)
                if (idx == 0) trackPath.moveTo(p.x, p.y) else trackPath.lineTo(p.x, p.y)
            }
            trackPath.close()
            drawPath(
                path = trackPath,
                color = VintageInk.copy(alpha = 0.75f),
                style = Stroke(width = cell * 0.075f, cap = StrokeCap.Round)
            )

            // Connecting lines into the 4 colored finish columns
            val homeEntryIndices = mapOf(0 to 39, 1 to 9, 2 to 19, 3 to 29)
            for (seat in 0 until 4) {
                val palette = seatPalette(seat)
                val entryCell = MenchRulesEngine.mainTrackCells[homeEntryIndices[seat] ?: 0]
                val finishList = MenchRulesEngine.homeColumnCells[seat].orEmpty()
                if (finishList.isNotEmpty()) {
                    val startPt = cellCenter(entryCell)
                    val endPt = cellCenter(finishList.last())
                    drawLine(
                        color = palette.main.copy(alpha = 0.6f),
                        start = startPt,
                        end = endPt,
                        strokeWidth = cell * 0.08f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // 4. Draw all 40 Outer Track Circles (classic circular old-school Mensch holes!)
            val startSeatByTrackIndex = mapOf(0 to 0, 10 to 1, 20 to 2, 30 to 3)
            MenchRulesEngine.mainTrackCells.forEachIndexed { idx, gc ->
                val c = cellCenter(gc)
                val startSeat = startSeatByTrackIndex[idx]
                val radius = cell * 0.34f

                // Recessed vintage shadow beneath hole
                drawCircle(
                    color = VintageInk.copy(alpha = 0.16f),
                    radius = radius,
                    center = Offset(c.x + 1.5f, c.y + 1.8f)
                )

                if (startSeat != null) {
                    val pal = seatPalette(startSeat)
                    drawCircle(color = pal.main, radius = radius * 1.06f, center = c)
                    drawCircle(
                        color = VintageTrackFill,
                        radius = radius * 0.78f,
                        center = c,
                        style = Stroke(width = 2.0f)
                    )
                    drawCircle(
                        color = VintageInk,
                        radius = radius * 1.06f,
                        center = c,
                        style = Stroke(width = 2.1f)
                    )
                    // Draw classic vintage direction arrow outside the Start circle
                    drawStartDirectionArrow(seat = startSeat, center = c, cell = cell, color = pal.dark)
                } else {
                    drawCircle(color = VintageTrackFill, radius = radius, center = c)
                    drawCircle(
                        color = VintageInk.copy(alpha = 0.88f),
                        radius = radius,
                        center = c,
                        style = Stroke(width = 1.9f)
                    )
                    // Subtle inner vintage ring
                    drawCircle(
                        color = ParchmentAged.copy(alpha = 0.6f),
                        radius = radius * 0.72f,
                        center = c,
                        style = Stroke(width = 1.0f)
                    )
                }
            }

            // 5. Draw 4 Colored Inner Finish Columns ("خانه‌های هدف")
            for (seat in 0 until 4) {
                val pal = seatPalette(seat)
                val finishList = MenchRulesEngine.homeColumnCells[seat].orEmpty()
                finishList.forEach { gc ->
                    val c = cellCenter(gc)
                    val radius = cell * 0.32f
                    drawCircle(
                        color = pal.main.copy(alpha = 0.90f),
                        radius = radius,
                        center = c
                    )
                    drawCircle(
                        color = VintageTrackFill.copy(alpha = 0.5f),
                        radius = radius * 0.68f,
                        center = c,
                        style = Stroke(width = 1.5f)
                    )
                    drawCircle(
                        color = VintageInk,
                        radius = radius,
                        center = c,
                        style = Stroke(width = 1.9f)
                    )
                }
            }

            // 6. Center Classic Persian Shamseh Star Medallion at (5, 5)
            drawPersianCenterRosette(
                center = cellCenter(GridCell(5, 5)),
                radius = cell * 0.44f
            )

            // 7. Draw All 16 Lacquered Wooden Pawns (movable pawns rendered last on top!)
            data class PawnRenderItem(
                val seat: Int,
                val tokenIndex: Int,
                val center: Offset,
                val movable: Boolean
            )

            val renderList = mutableListOf<PawnRenderItem>()
            for (seat in 0 until 4) {
                for (t in 0 until 4) {
                    val prog = tokens.getOrElse(seat * 4 + t) { -1 }
                    val gc = MenchRulesEngine.tokenGridCell(seat, t, prog)
                    val isMovable = (seat == mySeat && t in movableTokenIndices)
                    renderList.add(
                        PawnRenderItem(
                            seat = seat,
                            tokenIndex = t,
                            center = cellCenter(gc),
                            movable = isMovable
                        )
                    )
                }
            }

            // Sort so movable pawns draw on top for clear visibility
            renderList.sortedBy { if (it.movable) 1 else 0 }.forEach { item ->
                val pal = seatPalette(item.seat)
                val liftY = if (item.movable) -cell * 0.09f * bounceOffset else 0f
                val drawCenter = Offset(item.center.x, item.center.y + liftY)

                if (item.movable) {
                    // Pulsing golden selectable halo ring
                    drawCircle(
                        color = BrassGold.copy(alpha = 0.38f),
                        radius = cell * 0.44f * pulseScale,
                        center = item.center
                    )
                    drawCircle(
                        color = BrassGoldLight,
                        radius = cell * 0.38f * pulseScale,
                        center = item.center,
                        style = Stroke(width = 3.0f)
                    )
                }

                drawLacqueredPawn(
                    center = drawCenter,
                    radius = cell * 0.29f,
                    palette = pal,
                    isHighlighted = item.movable
                )
            }
        }
    }
}

private fun DrawScope.drawStartDirectionArrow(
    seat: Int,
    center: Offset,
    cell: Float,
    color: Color
) {
    // Classic small arrow just outside the starting circle showing clockwise travel direction
    val (offset, angleDeg) = when (seat) {
        0 -> Offset(-cell * 0.56f, 0f) to -90f // Red: UP
        1 -> Offset(0f, -cell * 0.56f) to 0f   // Blue: RIGHT
        2 -> Offset(cell * 0.56f, 0f) to 90f   // Yellow: DOWN
        else -> Offset(0f, cell * 0.56f) to 180f // Green: LEFT
    }
    val tipCenter = center + offset
    val rad = angleDeg * (PI / 180.0)
    val len = cell * 0.22f
    val tip = Offset(
        tipCenter.x + (cos(rad) * len).toFloat(),
        tipCenter.y + (sin(rad) * len).toFloat()
    )
    val left = Offset(
        tipCenter.x + (cos(rad + 2.4) * len * 0.85f).toFloat(),
        tipCenter.y + (sin(rad + 2.4) * len * 0.85f).toFloat()
    )
    val right = Offset(
        tipCenter.x + (cos(rad - 2.4) * len * 0.85f).toFloat(),
        tipCenter.y + (sin(rad - 2.4) * len * 0.85f).toFloat()
    )
    val arrowPath = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
    }
    drawPath(path = arrowPath, color = color)
}

private fun DrawScope.drawPersianCenterRosette(center: Offset, radius: Float) {
    // Outer antique gold ring
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(BrassGoldLight, BrassGold, BrassGoldDark),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
    drawCircle(
        color = VintageInk,
        radius = radius,
        center = center,
        style = Stroke(width = 2.0f)
    )

    // 8-pointed traditional star
    val starPath = Path()
    val points = 8
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) radius * 0.78f else radius * 0.38f
        val angle = (i * PI / points) - PI / 2.0
        val px = center.x + (cos(angle) * r).toFloat()
        val py = center.y + (sin(angle) * r).toFloat()
        if (i == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
    }
    starPath.close()
    drawPath(path = starPath, color = ParchmentLight)
    drawPath(path = starPath, color = VintageInk, style = Stroke(width = 1.4f))

    // Center jewel dot
    drawCircle(color = PawnRed, radius = radius * 0.18f, center = center)
}

private fun DrawScope.drawLacqueredPawn(
    center: Offset,
    radius: Float,
    palette: SeatPalette,
    isHighlighted: Boolean
) {
    // Soft tabletop drop shadow
    drawCircle(
        color = Color.Black.copy(alpha = 0.36f),
        radius = radius * 1.06f,
        center = Offset(center.x + radius * 0.16f, center.y + radius * 0.24f)
    )

    // Outer wooden base collar
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(palette.main, palette.dark),
            center = Offset(center.x - radius * 0.25f, center.y - radius * 0.25f),
            radius = radius * 1.2f
        ),
        radius = radius,
        center = center
    )
    drawCircle(
        color = if (isHighlighted) Color.White else VintageInk,
        radius = radius,
        center = center,
        style = Stroke(width = if (isHighlighted) 2.5f else 1.8f)
    )

    // Raised lacquered pawn head dome (classic top-down carved Mensch piece!)
    val domeRadius = radius * 0.64f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(palette.light, palette.main, palette.dark),
            center = Offset(center.x - domeRadius * 0.32f, center.y - domeRadius * 0.32f),
            radius = domeRadius * 1.25f
        ),
        radius = domeRadius,
        center = Offset(center.x, center.y - radius * 0.06f)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.65f),
        radius = domeRadius * 0.26f,
        center = Offset(center.x - domeRadius * 0.28f, center.y - domeRadius * 0.34f)
    )
}

@Composable
fun VintageBoneDice(
    faceValue: Int,
    isRolling: Boolean,
    isInteractive: Boolean,
    onRollClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "dice_anim")
    val rollAngle by infinite.animateFloat(
        initialValue = -14f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(110),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dice_spin"
    )
    val promptScale by infinite.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dice_prompt_scale"
    )

    val shownFace = faceValue.coerceIn(1, 6)
    val rotation = if (isRolling) rollAngle else 0f
    val scale = if (isInteractive && !isRolling) promptScale else 1f

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(64.dp)
            .scale(scale)
            .rotate(rotation)
            .shadow(if (isInteractive) 10.dp else 4.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFFFFFAEC), Color(0xFFECE0C3))
                )
            )
            .border(
                width = if (isInteractive) 2.5.dp else 1.5.dp,
                color = if (isInteractive) BrassGoldLight else ParchmentBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(enabled = isInteractive && !isRolling, onClick = onRollClick)
            .semantics { contentDescription = "تاس کلاسیک با عدد $shownFace" }
            .testTag("roll_dice_button")
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            val pipR = size.minDimension * 0.115f
            val left = size.width * 0.2f
            val midX = size.width * 0.5f
            val right = size.width * 0.8f
            val top = size.height * 0.2f
            val midY = size.height * 0.5f
            val bottom = size.height * 0.8f

            val pips = when (shownFace) {
                1 -> listOf(Offset(midX, midY))
                2 -> listOf(Offset(left, top), Offset(right, bottom))
                3 -> listOf(Offset(left, top), Offset(midX, midY), Offset(right, bottom))
                4 -> listOf(Offset(left, top), Offset(right, top), Offset(left, bottom), Offset(right, bottom))
                5 -> listOf(
                    Offset(left, top), Offset(right, top),
                    Offset(midX, midY),
                    Offset(left, bottom), Offset(right, bottom)
                )
                else -> listOf(
                    Offset(left, top), Offset(right, top),
                    Offset(left, midY), Offset(right, midY),
                    Offset(left, bottom), Offset(right, bottom)
                )
            }

            val pipColor = if (shownFace == 6 || shownFace == 1) PawnRed else VintageInk
            pips.forEach { pos ->
                drawCircle(color = pipColor, radius = pipR, center = pos)
                drawCircle(
                    color = Color.White.copy(alpha = 0.22f),
                    radius = pipR * 0.35f,
                    center = Offset(pos.x - pipR * 0.25f, pos.y - pipR * 0.25f)
                )
            }
        }
    }
}
