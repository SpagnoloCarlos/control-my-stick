package com.controlmystick.ui

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controlmystick.hid.ConnState
import com.controlmystick.hid.Consumer
import com.controlmystick.hid.HidRemoteService
import com.controlmystick.hid.Key
import kotlin.math.roundToInt

@Composable
fun RemoteScreen(
    service: HidRemoteService?,
    state: ConnState,
    onOpenPairing: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onQuit: () -> Unit,
) {
    var showKeyboard by rememberSaveable { mutableStateOf(false) }
    var touchMode by remember(service) { mutableStateOf(service?.touchMode ?: false) }

    // Acciones: teclas del teclado HID y usos Consumer (mantener = autorepeat del Stick)
    fun key(usage: Int) = Pair({ service?.pressKey(usage); Unit }, { service?.releaseKey(usage); Unit })
    fun consumer(usage: Int) = Pair({ service?.pressConsumer(usage); Unit }, { service?.releaseConsumer(); Unit })

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusBar(state, onOpenPairing, onEnableBluetooth, onQuit, onReconnect = { service?.reconnect() })

        Spacer(Modifier.height(12.dp))

        // Fila superior: Power, selector de modo y Buscar
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val (pd, pu) = consumer(Consumer.POWER)
            RoundButton(Icons.Filled.PowerSettingsNew, "Encendido", pd, pu, tint = Color(0xFFFF5252))
            NavModeToggle(touchMode) {
                touchMode = it
                service?.touchMode = it
            }
            val (sd, su) = consumer(Consumer.SEARCH)
            RoundButton(Icons.Filled.Search, "Buscar", sd, su)
        }

        Spacer(Modifier.height(20.dp))

        // Navegación: D-pad o zona táctil
        if (touchMode) {
            TouchPad(onNav = { service?.tapNav(it) })
        } else {
            DPad(
                up = key(Key.UP), down = key(Key.DOWN), left = key(Key.LEFT), right = key(Key.RIGHT),
                ok = key(Key.ENTER),
            )
        }

        Spacer(Modifier.height(24.dp))

        // Atrás / Inicio / Menú
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            val (bd, bu) = consumer(Consumer.BACK)
            RoundButton(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", bd, bu, size = 68.dp)
            val (hd, hu) = consumer(Consumer.HOME)
            RoundButton(Icons.Filled.Home, "Inicio", hd, hu, size = 68.dp)
            val (md, mu) = consumer(Consumer.MENU)
            RoundButton(Icons.Filled.Menu, "Menú", md, mu, size = 68.dp)
        }

        Spacer(Modifier.height(24.dp))

        // Volumen | Play/Pausa + Mute | Teclado
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VolumeRocker(consumer(Consumer.VOLUME_UP), consumer(Consumer.VOLUME_DOWN))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val (ppd, ppu) = consumer(Consumer.PLAY_PAUSE)
                HoldButton(ppd, ppu, Modifier.size(68.dp), CircleShape) {
                    Row {
                        Icon(Icons.Filled.PlayArrow, "Reproducir/Pausa", Modifier.size(22.dp))
                        Icon(Icons.Filled.Pause, null, Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                val (mtd, mtu) = consumer(Consumer.MUTE)
                RoundButton(Icons.AutoMirrored.Filled.VolumeOff, "Silenciar", mtd, mtu, size = 68.dp)
            }
            RoundButton(
                Icons.Filled.Keyboard, "Teclado",
                onDown = { showKeyboard = !showKeyboard }, onUp = {},
                size = 68.dp, tint = if (showKeyboard) Accent else Color.White,
            )
        }

        Spacer(Modifier.height(20.dp))

        // Multimedia: anterior / retroceder / adelantar / siguiente
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            val (a, b) = consumer(Consumer.PREV_TRACK)
            RoundButton(Icons.Filled.SkipPrevious, "Anterior", a, b, size = 54.dp)
            val (c, d) = consumer(Consumer.REWIND)
            RoundButton(Icons.Filled.FastRewind, "Retroceder", c, d, size = 54.dp)
            val (e, f) = consumer(Consumer.FAST_FORWARD)
            RoundButton(Icons.Filled.FastForward, "Adelantar", e, f, size = 54.dp)
            val (g, h) = consumer(Consumer.NEXT_TRACK)
            RoundButton(Icons.Filled.SkipNext, "Siguiente", g, h, size = 54.dp)
        }

        if (showKeyboard) {
            Spacer(Modifier.height(20.dp))
            TextSender(service)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusBar(
    state: ConnState,
    onOpenPairing: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onQuit: () -> Unit,
    onReconnect: () -> Unit,
) {
    val connected = state is ConnState.Connected
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .background(Surface1)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                when {
                    connected -> Icons.Filled.BluetoothConnected
                    state is ConnState.BluetoothOff || state is ConnState.Unsupported -> Icons.Filled.BluetoothDisabled
                    else -> Icons.Filled.Bluetooth
                },
                null,
                tint = if (connected) Color(0xFF4CAF50) else Color(0xFFB0B0B0),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(state.label(), maxLines = 1, fontSize = 14.sp, modifier = Modifier.weight(1f))
            when (state) {
                ConnState.BluetoothOff ->
                    TextButton(onClick = onEnableBluetooth) { Text("Activar") }
                ConnState.Ready ->
                    TextButton(onClick = onReconnect) { Text("Reconectar") }
                else -> {}
            }
        }
        IconButton(onClick = onOpenPairing) {
            Icon(Icons.Filled.Bluetooth, "Emparejar / conectar")
        }
        IconButton(onClick = onQuit) {
            Icon(Icons.Filled.Close, "Cerrar app")
        }
    }
    if (state is ConnState.Unsupported) {
        Text(
            state.reason,
            color = Color(0xFFFF8A80),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Botón que envía "presionado" al tocar y "soltado" al levantar el dedo. */
@Composable
fun HoldButton(
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    color: Color = Surface2,
    content: @Composable BoxScope.() -> Unit,
) {
    val view = LocalView.current
    val down by rememberUpdatedState(onDown)
    val up by rememberUpdatedState(onUp)
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(shape)
            .background(if (pressed) Accent.copy(alpha = 0.55f) else color)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    down()
                    try {
                        tryAwaitRelease()
                    } finally {
                        pressed = false
                        up()
                    }
                })
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    description: String,
    onDown: () -> Unit,
    onUp: () -> Unit,
    size: Dp = 60.dp,
    tint: Color = Color.White,
) {
    HoldButton(onDown, onUp, Modifier.size(size)) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(size * 0.42f))
    }
}

private typealias Action = Pair<() -> Unit, () -> Unit>

@Composable
private fun DPad(up: Action, down: Action, left: Action, right: Action, ok: Action) {
    val padSize = 270.dp
    val arm = 82.dp
    Box(
        Modifier
            .size(padSize)
            .clip(CircleShape)
            .background(Surface1)
            .border(2.dp, Surface2, CircleShape),
    ) {
        val armShape = RoundedCornerShape(24.dp)
        HoldButton(up.first, up.second, Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(arm), armShape, Color.Transparent) {
            Icon(Icons.Filled.KeyboardArrowUp, "Arriba", Modifier.size(44.dp))
        }
        HoldButton(down.first, down.second, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(arm), armShape, Color.Transparent) {
            Icon(Icons.Filled.KeyboardArrowDown, "Abajo", Modifier.size(44.dp))
        }
        HoldButton(left.first, left.second, Modifier.align(Alignment.CenterStart).padding(start = 6.dp).size(arm), armShape, Color.Transparent) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Izquierda", Modifier.size(44.dp))
        }
        HoldButton(right.first, right.second, Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(arm), armShape, Color.Transparent) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Derecha", Modifier.size(44.dp))
        }
        HoldButton(
            ok.first, ok.second,
            Modifier.align(Alignment.Center).size(96.dp).border(2.dp, Accent.copy(alpha = 0.6f), CircleShape),
        ) {
            Text("OK", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
    }
}

@Composable
private fun VolumeRocker(plus: Action, minus: Action) {
    Column(
        Modifier.width(68.dp).clip(RoundedCornerShape(34.dp)).background(Surface2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HoldButton(plus.first, plus.second, Modifier.size(68.dp, 76.dp), RoundedCornerShape(34.dp), Color.Transparent) {
            Icon(Icons.Filled.Add, "Subir volumen")
        }
        Text("VOL", fontSize = 11.sp, color = Color(0xFFB0B0B0))
        HoldButton(minus.first, minus.second, Modifier.size(68.dp, 76.dp), RoundedCornerShape(34.dp), Color.Transparent) {
            Icon(Icons.Filled.Remove, "Bajar volumen")
        }
    }
}

@Composable
private fun TextSender(service: HidRemoteService?) {
    var text by rememberSaveable { mutableStateOf("") }
    val progress by HidRemoteService.typingProgress.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val send = {
        if (text.isNotEmpty()) {
            service?.typeText(text)
            text = ""
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Escribí y tocá enviar: el texto se tipea en el campo seleccionado del Stick.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                val clip = clipboard.getText()?.text?.replace(Regex("[\\r\\n]+"), " ")?.trim()
                if (clip.isNullOrEmpty()) {
                    Toast.makeText(context, "El portapapeles está vacío", Toast.LENGTH_SHORT).show()
                } else {
                    text = clip
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.ContentPaste, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Pegar lo copiado")
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Buscar en el Stick…") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
            )
            IconButton(onClick = send) {
                Icon(Icons.AutoMirrored.Filled.Send, "Enviar texto", tint = Accent)
            }
        }
        progress?.let { p ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Enviando… ${p.sent}/${p.total}", fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { if (p.total == 0) 0f else p.sent.toFloat() / p.total },
                        modifier = Modifier.fillMaxWidth(),
                        color = Accent,
                    )
                }
                TextButton(onClick = { service?.stopTyping() }) { Text("Cancelar") }
            }
        }
        var delay by remember(service) {
            mutableFloatStateOf(
                (service?.typingDelayMs ?: HidRemoteService.DEFAULT_TYPING_DELAY_MS.toLong()).toFloat()
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Velocidad: ${delay.roundToInt()} ms por letra" +
                "  (si faltan letras, movelo a la derecha)",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = delay,
            onValueChange = { delay = it },
            onValueChangeFinished = { service?.typingDelayMs = delay.roundToInt().toLong() },
            valueRange = HidRemoteService.MIN_TYPING_DELAY_MS.toFloat()..HidRemoteService.MAX_TYPING_DELAY_MS.toFloat(),
            steps = 0,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { service?.tapKey(Key.BACKSPACE) }) {
                Icon(Icons.AutoMirrored.Filled.Backspace, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Borrar")
            }
            TextButton(onClick = { service?.tapKey(Key.ENTER) }) { Text("Enter") }
        }
    }
}
