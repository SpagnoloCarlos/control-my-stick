package com.controlmystick.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlmystick.hid.ConnState
import com.controlmystick.hid.HidRemoteService

val Accent = Color(0xFFFF6900)
val Surface1 = Color(0xFF1E2228)
val Surface2 = Color(0xFF2A3038)
val Background = Color(0xFF121417)

@Composable
fun RemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            background = Background,
            surface = Background,
            surfaceVariant = Surface1,
            surfaceContainer = Surface1,
            surfaceContainerHigh = Surface2,
        ),
        content = content
    )
}

@Composable
fun App(
    service: HidRemoteService?,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onQuit: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding()) {
            if (!permissionsGranted) {
                PermissionScreen(onRequestPermissions)
                return@Box
            }
            val state by HidRemoteService.state.collectAsStateWithLifecycle()
            var showPairing by rememberSaveable { mutableStateOf(false) }

            // La primera vez (sin Stick guardado) abrimos directamente el emparejamiento.
            LaunchedEffect(service) {
                if (service != null && service.savedHostAddress() == null) showPairing = true
            }

            if (showPairing) {
                BackHandler { showPairing = false }
                PairingScreen(
                    service = service,
                    state = state,
                    onMakeDiscoverable = onMakeDiscoverable,
                    onEnableBluetooth = onEnableBluetooth,
                    onClose = { showPairing = false },
                )
            } else {
                RemoteScreen(
                    service = service,
                    state = state,
                    onOpenPairing = { showPairing = true },
                    onEnableBluetooth = onEnableBluetooth,
                    onQuit = onQuit,
                )
            }
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Control Mi Stick", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text(
            "Para funcionar como control Bluetooth, la app necesita permiso para usar " +
                "Bluetooth y conectarse a dispositivos cercanos.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text("Dar permisos") }
    }
}

fun ConnState.label(): String = when (this) {
    ConnState.Stopped, ConnState.Starting -> "Iniciando…"
    ConnState.BluetoothOff -> "Bluetooth apagado"
    is ConnState.Unsupported -> "No disponible"
    ConnState.Ready -> "Sin conectar"
    is ConnState.Connecting -> "Conectando con $name…"
    is ConnState.Connected -> name
}
