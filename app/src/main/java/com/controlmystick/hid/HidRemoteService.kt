package com.controlmystick.hid

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.controlmystick.R
import com.controlmystick.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Estado de la conexión, observado por la UI. */
sealed interface ConnState {
    data object Stopped : ConnState
    data object Starting : ConnState
    data object BluetoothOff : ConnState
    data class Unsupported(val reason: String) : ConnState
    data object Ready : ConnState
    data class Connecting(val name: String) : ConnState
    data class Connected(val name: String, val address: String) : ConnState
}

data class TypingProgress(val sent: Int, val total: Int)

data class BtDevice(val name: String, val address: String, val bonded: Boolean, val isTv: Boolean)

/**
 * Servicio en primer plano que registra el teléfono como dispositivo HID Bluetooth
 * (teclado + controles multimedia) y mantiene la conexión con el Mi Stick.
 *
 * La Activity solo lo inicia una vez concedidos los permisos de Bluetooth.
 */
@SuppressLint("MissingPermission")
class HidRemoteService : Service() {

    inner class LocalBinder : Binder() {
        val service: HidRemoteService get() = this@HidRemoteService
    }

    companion object {
        private const val TAG = "HidRemote"
        private const val CHANNEL_ID = "remote"
        private const val NOTIF_ID = 1
        private const val PREFS = "remote"
        private const val PREF_HOST = "host_address"
        const val ACTION_STOP = "com.controlmystick.STOP"

        // Ritmo de tipeo del texto
        private const val PREF_TYPING_DELAY = "typing_delay_ms"
        const val DEFAULT_TYPING_DELAY_MS = 300
        const val MIN_TYPING_DELAY_MS = 60
        const val MAX_TYPING_DELAY_MS = 600
        private const val KEY_HOLD_MS = 40L
        private const val MOD_DELAY_MS = 20L
        private const val WAKE_DELAY_MS = 150L
        private const val SEND_RETRIES = 20
        private const val RETRY_DELAY_MS = 15L
        private const val NAV_HOLD_MS = 25L
        private const val NAV_GAP_MS = 15L
        private const val PREF_TOUCH_MODE = "touch_mode"

        private val _typingProgress = MutableStateFlow<TypingProgress?>(null)
        /** Progreso del texto que se está tipeando, o null si no hay ninguno. */
        val typingProgress: StateFlow<TypingProgress?> = _typingProgress

        private val _state = MutableStateFlow<ConnState>(ConnState.Stopped)
        val state: StateFlow<ConnState> = _state

        private val _discovered = MutableStateFlow<List<BtDevice>>(emptyList())
        val discovered: StateFlow<List<BtDevice>> = _discovered

        private val _discovering = MutableStateFlow(false)
        val discovering: StateFlow<Boolean> = _discovering
    }

    private val binder = LocalBinder()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val callbackExecutor = Executors.newSingleThreadExecutor()
    private val typingExecutor = Executors.newSingleThreadExecutor()
    private val navExecutor = Executors.newSingleThreadExecutor()
    private val cancelTyping = AtomicBoolean(false)
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    private var adapter: BluetoothAdapter? = null
    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private var registered = false
    private var pendingConnect: BluetoothDevice? = null

    // Estado del teclado (protegido por keyLock)
    private val keyLock = Any()
    private val pressedKeys = LinkedHashSet<Int>()
    private var modifiers = 0

    // ------------------------------------------------------------------ ciclo de vida

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification("Iniciando…"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        )

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(this, btReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        adapter = getSystemService(BluetoothManager::class.java)?.adapter
        val a = adapter
        when {
            a == null -> setState(ConnState.Unsupported("Este teléfono no tiene Bluetooth."))
            !a.isEnabled -> setState(ConnState.BluetoothOff)
            else -> initProfile()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        runCatching { unregisterReceiver(btReceiver) }
        runCatching { adapter?.cancelDiscovery() }
        teardownProfile()
        callbackExecutor.shutdownNow()
        typingExecutor.shutdownNow()
        navExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        _discovering.value = false
        _state.value = ConnState.Stopped
        super.onDestroy()
    }

    // ------------------------------------------------------------------ perfil HID

    private fun initProfile() {
        setState(ConnState.Starting)
        val ok = runCatching {
            adapter!!.getProfileProxy(this, profileListener, BluetoothProfile.HID_DEVICE)
        }.getOrDefault(false)
        if (!ok) {
            setState(ConnState.Unsupported("Este teléfono no soporta el perfil Bluetooth HID Device."))
            return
        }
        // Algunos fabricantes nunca responden: avisamos tras unos segundos.
        mainHandler.postDelayed({
            if (hid == null && _state.value == ConnState.Starting) {
                setState(ConnState.Unsupported("El perfil Bluetooth HID Device no respondió. Es posible que el fabricante lo haya desactivado."))
            }
        }, 5000)
    }

    private fun teardownProfile() {
        val h = hid
        if (h != null) {
            runCatching { host?.let { h.disconnect(it) } }
            runCatching { h.unregisterApp() }
            runCatching { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, h) }
        }
        hid = null
        host = null
        registered = false
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hid = proxy as BluetoothHidDevice
            registerApp()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hid = null
            host = null
            registered = false
            if (adapter?.isEnabled == true) setState(ConnState.Starting)
        }
    }

    private fun registerApp() {
        val h = hid ?: return
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Control Mi Stick",
            "Control remoto Bluetooth",
            "ControlMyStick",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HidDescriptor.DESCRIPTOR
        )
        val ok = runCatching { h.registerApp(sdp, null, null, callbackExecutor, hidCallback) }
            .getOrDefault(false)
        if (!ok) {
            setState(ConnState.Unsupported("No se pudo registrar el control HID. Cerrá otras apps de teclado/mouse Bluetooth y probá de nuevo."))
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "onAppStatusChanged registered=$registered plugged=${pluggedDevice?.address}")
            this@HidRemoteService.registered = registered
            if (!registered) {
                host = null
                if (_state.value !is ConnState.Unsupported) setState(ConnState.Starting)
                return
            }
            if (host == null) setState(ConnState.Ready)
            val target = pendingConnect ?: savedHost()
            pendingConnect = null
            if (target != null && host == null) connectInternal(target)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.d(TAG, "onConnectionStateChanged ${device.address} -> $state")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    host = device
                    prefs.edit().putString(PREF_HOST, device.address).apply()
                    setState(ConnState.Connected(device.displayName(), device.address))
                }
                BluetoothProfile.STATE_CONNECTING ->
                    if (host == null) setState(ConnState.Connecting(device.displayName()))
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (host == null || host?.address == device.address) {
                        host = null
                        clearKeys()
                        if (registered) setState(ConnState.Ready)
                    }
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            val size = if (id.toInt() == HidDescriptor.REPORT_ID_CONSUMER)
                HidDescriptor.CONSUMER_REPORT_SIZE else HidDescriptor.KEYBOARD_REPORT_SIZE
            runCatching { hid?.replyReport(device, type, id, ByteArray(size)) }
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            // LEDs del teclado (Caps Lock, etc.): los aceptamos sin hacer nada.
            runCatching { hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS) }
        }
    }

    // ------------------------------------------------------------------ conexión (API pública)

    fun bondedDevices(): List<BtDevice> =
        runCatching { adapter?.bondedDevices.orEmpty().map { it.toBtDevice() } }
            .getOrDefault(emptyList())
            .sortedByDescending { it.isTv }

    /** Conecta con un dispositivo; si no está emparejado, primero inicia el emparejamiento. */
    fun connect(address: String) {
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return
        runCatching { adapter?.cancelDiscovery() }
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            connectInternal(device)
        } else {
            pendingConnect = device
            setState(ConnState.Connecting(device.displayName()))
            device.createBond()
        }
    }

    fun disconnect() {
        val h = hid ?: return
        host?.let { runCatching { h.disconnect(it) } }
    }

    fun reconnect() {
        savedHost()?.let { connectInternal(it) }
    }

    fun savedHostAddress(): String? = prefs.getString(PREF_HOST, null)

    fun startDiscovery() {
        val a = adapter ?: return
        _discovered.value = emptyList()
        runCatching {
            if (a.isDiscovering) a.cancelDiscovery()
            a.startDiscovery()
        }.onFailure { Log.w(TAG, "startDiscovery", it) }
    }

    fun stopDiscovery() {
        runCatching { adapter?.cancelDiscovery() }
    }

    private fun connectInternal(device: BluetoothDevice) {
        val h = hid
        if (h == null || !registered) {
            pendingConnect = device
            return
        }
        if (host?.address == device.address) return
        setState(ConnState.Connecting(device.displayName()))
        val ok = runCatching { h.connect(device) }.getOrDefault(false)
        if (!ok) setState(ConnState.Ready)
    }

    private fun savedHost(): BluetoothDevice? {
        val addr = savedHostAddress() ?: return null
        val device = runCatching { adapter?.getRemoteDevice(addr) }.getOrNull() ?: return null
        return device.takeIf { it.bondState == BluetoothDevice.BOND_BONDED }
    }

    // ------------------------------------------------------------------ envío de teclas

    fun pressKey(usage: Int) = synchronized(keyLock) {
        if (pressedKeys.size < 6) pressedKeys.add(usage)
        sendKeyboardLocked()
    }

    fun releaseKey(usage: Int) = synchronized(keyLock) {
        pressedKeys.remove(usage)
        sendKeyboardLocked()
    }

    fun pressConsumer(usage: Int) = sendConsumer(usage)

    fun releaseConsumer() = sendConsumer(0)

    /**
     * Escribe un texto en el Stick tecla por tecla (en segundo plano).
     *
     * Va a un ritmo "humano" y reintenta cada reporte: si se envía demasiado rápido,
     * la cola Bluetooth se llena, sendReport devuelve false y se pierden letras.
     */
    fun typeText(text: String) {
        val normalized = KeyMapper.normalize(text)
        if (normalized.isEmpty()) return
        cancelTyping.set(false)
        typingExecutor.execute {
            val total = normalized.length
            _typingProgress.value = TypingProgress(0, total)
            try {
                // "Despierta" el enlace (puede estar en modo de bajo consumo) antes de la primera letra.
                sendReliable(HidDescriptor.REPORT_ID_KEYBOARD, ByteArray(HidDescriptor.KEYBOARD_REPORT_SIZE))
                Thread.sleep(WAKE_DELAY_MS)
                Log.d(TAG, "typeText ($total caracteres) a $typingDelayMs ms/letra")
                normalized.forEachIndexed { i, c ->
                    if (cancelTyping.get() || host == null) return@execute
                    KeyMapper.map(c)?.let { strokeKey(it.modifiers, it.usage) }
                    _typingProgress.value = TypingProgress(i + 1, total)
                }
            } catch (_: InterruptedException) {
                // servicio detenido
            } finally {
                sendRawKeyboard(0, 0)
                _typingProgress.value = null
            }
        }
    }

    /** Detiene el texto que se está enviando. */
    fun stopTyping() = cancelTyping.set(true)

    fun tapKey(usage: Int) {
        typingExecutor.execute { strokeKey(0, usage) }
    }

    /**
     * Toque rápido de navegación (zona táctil). Usa su propio hilo para no quedar
     * detrás del texto, y sin la pausa larga entre letras.
     */
    fun tapNav(usage: Int) {
        navExecutor.execute {
            pressKey(usage)
            Thread.sleep(NAV_HOLD_MS)
            releaseKey(usage)
            Thread.sleep(NAV_GAP_MS)
        }
    }

    /** Modo de navegación preferido: true = zona táctil, false = D-pad. */
    var touchMode: Boolean
        get() = prefs.getBoolean(PREF_TOUCH_MODE, false)
        set(value) = prefs.edit().putBoolean(PREF_TOUCH_MODE, value).apply()

    private fun strokeKey(mods: Int, usage: Int) {
        if (mods != 0) {
            // Primero solo el modificador (Shift), así el Stick lo registra antes que la letra.
            sendRawKeyboard(mods, 0)
            Thread.sleep(MOD_DELAY_MS)
        }
        sendRawKeyboard(mods, usage)
        Thread.sleep(KEY_HOLD_MS)
        if (mods != 0) {
            sendRawKeyboard(mods, 0)
            Thread.sleep(MOD_DELAY_MS)
        }
        sendRawKeyboard(0, 0)
        // Pausa entre letras: las apps del Stick (búsquedas con sugerencias) descartan
        // teclas si llegan más rápido de lo que procesan.
        Thread.sleep((typingDelayMs - KEY_HOLD_MS).coerceAtLeast(20L))
    }

    /** Tiempo total por letra, configurable desde la UI y guardado entre sesiones. */
    var typingDelayMs: Long
        get() = prefs.getInt(PREF_TYPING_DELAY, DEFAULT_TYPING_DELAY_MS).toLong()
        set(value) {
            val v = value.toInt().coerceIn(MIN_TYPING_DELAY_MS, MAX_TYPING_DELAY_MS)
            prefs.edit().putInt(PREF_TYPING_DELAY, v).apply()
        }

    private fun sendKeyboardLocked() {
        val report = ByteArray(HidDescriptor.KEYBOARD_REPORT_SIZE)
        report[0] = modifiers.toByte()
        pressedKeys.forEachIndexed { i, k -> report[2 + i] = k.toByte() }
        send(HidDescriptor.REPORT_ID_KEYBOARD, report)
    }

    private fun sendRawKeyboard(mods: Int, usage: Int) {
        val report = ByteArray(HidDescriptor.KEYBOARD_REPORT_SIZE)
        report[0] = mods.toByte()
        report[2] = usage.toByte()
        sendReliable(HidDescriptor.REPORT_ID_KEYBOARD, report)
    }

    /** Reintenta el envío mientras la cola Bluetooth esté ocupada (sólo desde typingExecutor). */
    private fun sendReliable(reportId: Int, data: ByteArray): Boolean {
        repeat(SEND_RETRIES) {
            if (host == null) return false
            if (send(reportId, data)) return true
            Thread.sleep(RETRY_DELAY_MS)
        }
        Log.w(TAG, "Reporte descartado tras $SEND_RETRIES intentos")
        return false
    }

    private fun sendConsumer(usage: Int) {
        val report = byteArrayOf((usage and 0xFF).toByte(), ((usage shr 8) and 0xFF).toByte())
        send(HidDescriptor.REPORT_ID_CONSUMER, report)
    }

    private fun clearKeys() = synchronized(keyLock) {
        pressedKeys.clear()
        modifiers = 0
    }

    private fun send(reportId: Int, data: ByteArray): Boolean {
        val h = hid ?: return false
        val d = host ?: return false
        return runCatching { h.sendReport(d, reportId, data) }.getOrDefault(false)
    }

    // ------------------------------------------------------------------ broadcasts

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                        BluetoothAdapter.STATE_ON -> if (hid == null) initProfile()
                        BluetoothAdapter.STATE_OFF -> {
                            hid = null; host = null; registered = false
                            setState(ConnState.BluetoothOff)
                        }
                    }
                }
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.device() ?: return
                    val bt = device.toBtDevice()
                    _discovered.update { list ->
                        (list.filterNot { it.address == bt.address } + bt)
                            .sortedWith(compareByDescending<BtDevice> { it.isTv }.thenBy { it.name })
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> _discovering.value = true
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> _discovering.value = false
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = intent.device() ?: return
                    val bond = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                    val pending = pendingConnect
                    if (pending != null && pending.address == device.address) {
                        when (bond) {
                            BluetoothDevice.BOND_BONDED -> {
                                pendingConnect = null
                                connectInternal(device)
                            }
                            BluetoothDevice.BOND_NONE -> {
                                pendingConnect = null
                                if (host == null && registered) setState(ConnState.Ready)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun Intent.device(): BluetoothDevice? =
        IntentCompat.getParcelableExtra(this, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)

    // ------------------------------------------------------------------ utilidades

    private fun BluetoothDevice.displayName(): String =
        runCatching { name }.getOrNull()?.takeIf { it.isNotBlank() } ?: address

    private fun BluetoothDevice.toBtDevice(): BtDevice {
        val major = runCatching { bluetoothClass?.majorDeviceClass }.getOrNull()
        val n = displayName()
        val looksLikeTv = major == BluetoothClass.Device.Major.AUDIO_VIDEO ||
            listOf("stick", "tv", "mibox", "mi box", "xiaomi").any { n.contains(it, ignoreCase = true) }
        return BtDevice(n, address, bondState == BluetoothDevice.BOND_BONDED, looksLikeTv)
    }

    private fun setState(s: ConnState) {
        _state.value = s
        val text = when (s) {
            ConnState.Stopped, ConnState.Starting -> "Iniciando…"
            ConnState.BluetoothOff -> "Bluetooth apagado"
            is ConnState.Unsupported -> "No disponible"
            ConnState.Ready -> "Listo · sin conectar"
            is ConnState.Connecting -> "Conectando con ${s.name}…"
            is ConnState.Connected -> "Conectado a ${s.name}"
        }
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIF_ID, buildNotification(text))
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Control remoto", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Mantiene la conexión Bluetooth con el TV Stick" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, HidRemoteService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_remote)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Cerrar", stop)
            .build()
    }
}
