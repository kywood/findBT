package com.gamenuri.btscanner24.core.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.gamenuri.btscanner24.core.model.DeviceCategory
import com.gamenuri.btscanner24.core.model.PairedDevice

/** Reads the phone's paired Bluetooth devices for My Devices. */
class BondedDevices(context: Context) {

    private val appContext = context.applicationContext
    private val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter

    /** Android 12+ needs BLUETOOTH_CONNECT to read paired devices; older versions only BLUETOOTH. */
    fun hasPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

    /** Empty when Bluetooth is off, unsupported or the permission is missing. */
    @SuppressLint("MissingPermission") // checked by hasPermission()
    fun list(): List<PairedDevice> {
        if (!hasPermission()) return emptyList()
        val bonded: Set<BluetoothDevice> = try {
            adapter?.bondedDevices.orEmpty()
        } catch (_: SecurityException) {
            emptySet()
        }
        return bonded.map { device ->
            // The alias is what the user renamed it to in Bluetooth settings ("wood's Buds2"); API 30+.
            val name = try {
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) device.alias else null) ?: device.name
            } catch (_: SecurityException) {
                null
            }
            val btClass = try {
                device.bluetoothClass
            } catch (_: SecurityException) {
                null
            }
            val fromClass = categoryOf(btClass)
            val category = if (fromClass == DeviceCategory.UNKNOWN) DeviceCategoryResolver.fromName(name) else fromClass
            PairedDevice(address = device.address, name = name, category = category)
        }
    }

    private companion object {
        fun categoryOf(btClass: BluetoothClass?): DeviceCategory {
            val cls = btClass ?: return DeviceCategory.UNKNOWN
            return when (cls.majorDeviceClass) {
                BluetoothClass.Device.Major.AUDIO_VIDEO -> when (cls.deviceClass) {
                    BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
                    BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
                    BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO,
                    -> DeviceCategory.SPEAKER
                    BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> DeviceCategory.HEADPHONES
                    BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> DeviceCategory.UNKNOWN
                    BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER,
                    BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR,
                    -> DeviceCategory.TV
                    // Most true-wireless earbuds report "wearable headset" or "hands-free".
                    else -> DeviceCategory.EARBUDS
                }
                BluetoothClass.Device.Major.WEARABLE -> when (cls.deviceClass) {
                    BluetoothClass.Device.WEARABLE_WRIST_WATCH -> DeviceCategory.WATCH
                    else -> DeviceCategory.FITNESS_BAND
                }
                BluetoothClass.Device.Major.HEALTH -> DeviceCategory.HEALTH
                BluetoothClass.Device.Major.PHONE -> DeviceCategory.PHONE
                BluetoothClass.Device.Major.COMPUTER -> DeviceCategory.COMPUTER
                BluetoothClass.Device.Major.TOY -> DeviceCategory.GAMEPAD
                BluetoothClass.Device.Major.PERIPHERAL -> peripheral(cls.deviceClass)
                else -> DeviceCategory.UNKNOWN
            }
        }

        // Peripheral minor class: bit 6 = keyboard, bit 7 = pointing device (Bluetooth Assigned Numbers, CoD).
        fun peripheral(deviceClass: Int): DeviceCategory = when {
            deviceClass and 0x40 != 0 -> DeviceCategory.KEYBOARD
            deviceClass and 0x80 != 0 -> DeviceCategory.MOUSE
            else -> DeviceCategory.GAMEPAD
        }
    }
}
