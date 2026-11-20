package com.controlmystick.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.controlmystick.hid.HidRemoteService

class MainActivity : ComponentActivity() {

    private var service by mutableStateOf<HidRemoteService?>(null)
    private var permissionsGranted by mutableStateOf(false)
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as HidRemoteService.LocalBinder).service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionsGranted = hasBluetoothPermissions()
            if (permissionsGranted) startAndBind()
        }

    private val enableBtLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    private val discoverableLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "No se activó la visibilidad", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        permissionsGranted = hasBluetoothPermissions()

        setContent {
            RemoteTheme {
                App(
                    service = service,
                    permissionsGranted = permissionsGranted,
                    onRequestPermissions = ::requestPermissions,
                    onEnableBluetooth = ::enableBluetooth,
                    onMakeDiscoverable = ::makeDiscoverable,
                    onQuit = ::quit,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (hasBluetoothPermissions()) {
            permissionsGranted = true
            startAndBind()
        }
    }

    override fun onStop() {
        if (bound) {
            unbindService(connection)
            bound = false
        }
        super.onStop()
    }

    private fun requiredPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }.toTypedArray()

    private fun hasBluetoothPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        val perms = requiredPermissions().toMutableList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun startAndBind() {
        val intent = Intent(this, HidRemoteService::class.java)
        ContextCompat.startForegroundService(this, intent)
        if (!bound) {
            bound = bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun enableBluetooth() {
        runCatching {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    private fun makeDiscoverable() {
        runCatching {
            discoverableLauncher.launch(
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
            )
        }
    }

    private fun quit() {
        if (bound) {
            unbindService(connection)
            bound = false
        }
        service = null
        stopService(Intent(this, HidRemoteService::class.java))
        finish()
    }
}
