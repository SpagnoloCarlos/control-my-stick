package com.controlmystick.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlmystick.hid.BtDevice
import com.controlmystick.hid.ConnState
import com.controlmystick.hid.HidRemoteService

@Composable
fun PairingScreen(
    service: HidRemoteService?,
    state: ConnState,
    onMakeDiscoverable: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onClose: () -> Unit,
) {
    val discovered by HidRemoteService.discovered.collectAsStateWithLifecycle()
    val discovering by HidRemoteService.discovering.collectAsStateWithLifecycle()
    var bonded by remember { mutableStateOf<List<BtDevice>>(emptyList()) }

    // Refrescamos la lista de emparejados cada vez que cambia el estado
    LaunchedEffect(service, state) { bonded = service?.bondedDevices().orEmpty() }
    DisposableEffect(service) { onDispose { service?.stopDiscovery() } }

    // Volvemos al control automáticamente sólo cuando la conexión se establece
    // estando en esta pantalla (no si ya estaba conectado al abrirla).
    val wasConnectedOnOpen = remember { state is ConnState.Connected }
    var sawDisconnected by remember { mutableStateOf(!wasConnectedOnOpen) }
    LaunchedEffect(state) {
        if (state !is ConnState.Connected) sawDisconnected = true
        else if (sawDisconnected) onClose()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
            Text("Conectar con el Mi Stick", style = MaterialTheme.typography.titleLarge)
        }

        Text(
            "Estado: ${state.label()}",
            color = if (state is ConnState.Connected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        if (state is ConnState.Unsupported) {
            Text(state.reason, color = Color(0xFFFF8A80), fontSize = 14.sp)
        }
        if (state is ConnState.BluetoothOff) {
            Button(onClick = onEnableBluetooth) { Text("Activar Bluetooth") }
        }

        Section("Opción 1 · Emparejar desde el Stick (recomendado)")
        Step("1", "Tocá «Hacer visible» para que el Stick encuentre el teléfono durante 2 minutos.")
        Step("2", "En el Stick, con el control original, entrá a Ajustes → Controles y accesorios → Agregar accesorio.")
        Step("3", "Elegí este teléfono en la lista y aceptá el código de emparejamiento en los dos equipos.")
        Step("4", "Cuando se conecte, esta pantalla se cierra sola y ya podés usar el control.")
        Spacer(Modifier.height(8.dp))
        Button(onClick = onMakeDiscoverable, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Bluetooth, null)
            Spacer(Modifier.width(8.dp))
            Text("Hacer visible (2 min)")
        }

        Section("Opción 2 · Elegir el Stick desde el teléfono")
        Text(
            "Si el Stick ya está emparejado con este teléfono, tocalo para conectar. Si no aparece, buscá dispositivos " +
                "mientras el Stick está en la pantalla «Agregar accesorio».",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        Text("Emparejados", fontWeight = FontWeight.SemiBold)
        if (bonded.isEmpty()) {
            Text("Ninguno todavía.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        bonded.forEach { d -> DeviceRow(d, state) { service?.connect(d.address) } }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cercanos", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (discovering) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        val nearby = discovered.filterNot { d -> bonded.any { it.address == d.address } }
        nearby.forEach { d -> DeviceRow(d, state) { service?.connect(d.address) } }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { if (discovering) service?.stopDiscovery() else service?.startDiscovery() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.AutoMirrored.Filled.BluetoothSearching, null)
            Spacer(Modifier.width(8.dp))
            Text(if (discovering) "Detener búsqueda" else "Buscar dispositivos")
        }

        if (state is ConnState.Connected) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { service?.disconnect() }, modifier = Modifier.fillMaxWidth()) {
                Text("Desconectar de ${state.name}")
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, color = Accent)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Step(n: String, text: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text("$n.", fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
        Text(text, fontSize = 14.sp)
    }
}

@Composable
private fun DeviceRow(device: BtDevice, state: ConnState, onClick: () -> Unit) {
    val connected = state is ConnState.Connected && state.address == device.address
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface1)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (device.isTv) Icons.Filled.Tv else Icons.Filled.Bluetooth, null,
            tint = if (device.isTv) Accent else Color(0xFFB0B0B0),
        )
        Column(Modifier.weight(1f)) {
            Text(device.name)
            Text(device.address, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (connected) Text("Conectado", color = Color(0xFF4CAF50), fontSize = 13.sp)
    }
}
