package com.xavadigital.mileagetracker.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xavadigital.mileagetracker.data.AppGraph
import kotlinx.coroutines.launch

private data class BondedDevice(val name: String, val address: String)

private fun bondedDevices(context: Context): List<BondedDevice> = try {
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    adapter?.bondedDevices.orEmpty().map { BondedDevice(it.name ?: it.address, it.address) }
} catch (_: SecurityException) {
    emptyList()
}

private fun hasBtConnectPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

private fun hasBackgroundLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

/**
 * Registers the car as a companion device so Android lets us start the recording
 * foreground service from a background Bluetooth broadcast (API 31+ restriction).
 */
private fun associateCarDevice(
    context: Context,
    address: String,
    launch: (IntentSender) -> Unit,
) {
    val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return
    val request = AssociationRequest.Builder()
        .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(address).build())
        .setSingleDevice(true)
        .build()
    @Suppress("DEPRECATION")
    cdm.associate(
        request,
        object : CompanionDeviceManager.Callback() {
            @Deprecated("Deprecated in Java")
            override fun onDeviceFound(chooserLauncher: IntentSender) {
                launch(chooserLauncher)
            }

            override fun onFailure(error: CharSequence?) {
                // Auto-start falls back to the "Driving?" notification without it.
            }
        },
        null
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = AppGraph.settings

    val autoTrack by settings.autoTrackEnabled.collectAsState(initial = false)
    val carName by settings.carName.collectAsState(initial = null)
    val carAddress by settings.carAddress.collectAsState(initial = null)
    val driverName by settings.driverName.collectAsState(initial = "")

    // Permission states can change in system settings — re-check on every resume.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val btGranted = remember(refresh) { hasBtConnectPermission(context) }
    val bgLocationGranted = remember(refresh) { hasBackgroundLocation(context) }
    val batteryExempt = remember(refresh) { isIgnoringBatteryOptimizations(context) }

    var showDevicePicker by remember { mutableStateOf(false) }

    val cdmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { }
    val btPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refresh++
        if (granted) showDevicePicker = true
    }
    val bgLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Automatic trip detection", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-record trips", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (carName != null) "Trigger: $carName" else "Choose your car below first",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoTrack,
                    enabled = carAddress != null,
                    onCheckedChange = { scope.launch { settings.setAutoTrackEnabled(it) } }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Car Bluetooth device", fontWeight = FontWeight.SemiBold)
                    Text(
                        carName ?: "Not set",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = {
                    if (btGranted) {
                        showDevicePicker = true
                    } else {
                        btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                }) { Text(if (carName == null) "Choose" else "Change") }
            }

            HorizontalDivider()
            Text("Permissions for auto-detection", style = MaterialTheme.typography.titleMedium)

            PermissionRow(
                title = "Background location",
                subtitle = "“Allow all the time” — needed to start recording when the car connects",
                granted = bgLocationGranted,
                onRequest = {
                    bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            )
            PermissionRow(
                title = "Battery optimisation exemption",
                subtitle = "Stops Android killing trip detection in the background",
                granted = batteryExempt,
                onRequest = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            )

            HorizontalDivider()
            Text("Driver", style = MaterialTheme.typography.titleMedium)

            var nameDraft by remember(driverName) { mutableStateOf(driverName) }
            OutlinedTextField(
                value = nameDraft,
                onValueChange = { nameDraft = it },
                label = { Text("Driver name on this phone") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (nameDraft.trim() != driverName && nameDraft.isNotBlank()) {
                        TextButton(onClick = {
                            scope.launch { settings.setDriverName(nameDraft) }
                        }) { Text("Save") }
                    }
                }
            )
        }
    }

    if (showDevicePicker) {
        val devices = remember { bondedDevices(context) }
        AlertDialog(
            onDismissRequest = { showDevicePicker = false },
            title = { Text("Choose your car") },
            text = {
                if (devices.isEmpty()) {
                    Text(
                        "No paired Bluetooth devices found. Pair your phone with the " +
                            "car's Bluetooth first, then come back here."
                    )
                } else {
                    Column {
                        devices.forEach { device ->
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        settings.setCarDevice(device.address, device.name)
                                    }
                                    showDevicePicker = false
                                    // Companion association → background auto-start allowed.
                                    associateCarDevice(context, device.address) { sender ->
                                        cdmLauncher.launch(
                                            IntentSenderRequest.Builder(sender).build()
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(device.name) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevicePicker = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    onRequest: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (granted) {
            Text("Granted", color = MaterialTheme.colorScheme.primary)
        } else {
            TextButton(onClick = onRequest) { Text("Grant") }
        }
    }
}
