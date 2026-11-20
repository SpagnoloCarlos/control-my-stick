package com.controlmystick.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controlmystick.hid.Key
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Zona táctil de navegación:
 *  - Deslizar el dedo mueve la selección un paso cada cierta distancia.
 *  - Dejar el dedo quieto cerca de un borde repite el movimiento en esa dirección.
 *  - Un toque corto es OK.
 */
@Composable
fun TouchPad(onNav: (Int) -> Unit) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val nav by rememberUpdatedState(onNav)

    var touching by remember { mutableStateOf(false) }
    var flashDir by remember { mutableIntStateOf(0) }
    var flashCounter by remember { mutableIntStateOf(0) }

    LaunchedEffect(flashCounter) {
        if (flashCounter > 0) {
            delay(220)
            flashDir = 0
        }
    }

    fun fire(dir: Int) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        nav(dir)
        flashDir = dir
        flashCounter++
    }

    Box(
        Modifier
            .size(270.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(if (touching) Surface2 else Surface1)
            .border(2.dp, if (touching) Accent.copy(alpha = 0.6f) else Surface2, RoundedCornerShape(36.dp))
            .pointerInput(Unit) {
                val step = 44.dp.toPx()          // distancia por paso
                val edge = 0.22f                  // franja del borde que activa la repetición
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown()
                    touching = true
                    val downTime = down.uptimeMillis
                    var upTime = downTime
                    var acc = Offset.Zero
                    var totalMove = 0f
                    var steps = 0
                    var lastDir = 0
                    var pos = down.position
                    var repeatJob: Job? = null

                    fun nearEdge(dir: Int): Boolean {
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        return when (dir) {
                            Key.LEFT -> pos.x < w * edge
                            Key.RIGHT -> pos.x > w * (1 - edge)
                            Key.UP -> pos.y < h * edge
                            Key.DOWN -> pos.y > h * (1 - edge)
                            else -> false
                        }
                    }

                    fun scheduleRepeat() {
                        repeatJob?.cancel()
                        val dir = lastDir
                        if (!nearEdge(dir)) return
                        repeatJob = scope.launch {
                            delay(400)
                            while (isActive) {
                                fire(dir)
                                delay(130)
                            }
                        }
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        upTime = change.uptimeMillis
                        if (!change.pressed) break
                        val d = change.positionChange()
                        if (d == Offset.Zero) continue
                        change.consume()
                        pos = change.position
                        acc += d
                        totalMove += d.getDistance()

                        var stepped = false
                        while (abs(acc.x) >= step || abs(acc.y) >= step) {
                            val dir: Int
                            if (abs(acc.x) >= abs(acc.y)) {
                                dir = if (acc.x > 0) Key.RIGHT else Key.LEFT
                                acc = Offset(acc.x - step * Math.signum(acc.x), 0f)
                            } else {
                                dir = if (acc.y > 0) Key.DOWN else Key.UP
                                acc = Offset(0f, acc.y - step * Math.signum(acc.y))
                            }
                            fire(dir)
                            lastDir = dir
                            steps++
                            stepped = true
                        }
                        if (stepped) {
                            scheduleRepeat()
                        } else if (repeatJob?.isActive == true && !nearEdge(lastDir)) {
                            // Salió del borde: dejamos de repetir.
                            repeatJob?.cancel()
                        }
                    }

                    repeatJob?.cancel()
                    touching = false
                    if (steps == 0 && totalMove < slop && upTime - downTime < 350) {
                        fire(Key.ENTER)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Flechas indicadoras que se iluminan con cada paso
        ArrowHint(Icons.Filled.KeyboardArrowUp, flashDir == Key.UP, Modifier.align(Alignment.TopCenter))
        ArrowHint(Icons.Filled.KeyboardArrowDown, flashDir == Key.DOWN, Modifier.align(Alignment.BottomCenter))
        ArrowHint(Icons.AutoMirrored.Filled.KeyboardArrowLeft, flashDir == Key.LEFT, Modifier.align(Alignment.CenterStart))
        ArrowHint(Icons.AutoMirrored.Filled.KeyboardArrowRight, flashDir == Key.RIGHT, Modifier.align(Alignment.CenterEnd))

        val okAlpha by animateFloatAsState(if (flashDir == Key.ENTER) 1f else 0.35f, label = "ok")
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("OK", fontSize = 20.sp, color = Color.White, modifier = Modifier.alpha(okAlpha))
            Text(
                "Deslizá para moverte\nTocá para OK\nMantené en el borde para repetir",
                fontSize = 12.sp,
                color = Color(0xFF8A8F98),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun ArrowHint(icon: androidx.compose.ui.graphics.vector.ImageVector, lit: Boolean, modifier: Modifier) {
    val a by animateFloatAsState(if (lit) 1f else 0.25f, label = "arrow")
    Icon(
        icon, null,
        tint = if (lit) Accent else Color.White,
        modifier = modifier.padding(10.dp).size(36.dp).alpha(a),
    )
}

/** Selector entre D-pad y zona táctil. */
@Composable
fun NavModeToggle(touchMode: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(Surface1).padding(4.dp),
    ) {
        ModeChip("Flechas", !touchMode) { onChange(false) }
        ModeChip("Táctil", touchMode) { onChange(true) }
    }
}

@Composable
private fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) Accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text, fontSize = 14.sp, color = if (selected) Color.White else Color(0xFFB0B0B0))
    }
}
