package com.rst.player.ui.components

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Trigger state for the headphone / Bluetooth connection overlay.
 *
 * The 3-second auto-dismiss is owned by the overlay composable (keyed on
 * [showCount]) so every show gets a full, uninterrupted 3 s regardless of
 * how fast devices reconnect.
 */
@Stable
class HeadphoneOverlayState {
    var visible by mutableStateOf(false)
        internal set
    var deviceName by mutableStateOf("")
        internal set
    var showCount by mutableStateOf(0)
        internal set

    internal fun show(name: String) {
        // Some devices fire the callback more than once per connection
        // (route changes, SCO/A2DP handoffs). Ignore duplicates while the
        // overlay is up so the show can't blink or overstay its 3 s.
        if (visible && name == deviceName) return
        deviceName = name
        showCount++
        visible = true
    }

    internal fun hide() {
        visible = false
    }
}

@Composable
fun rememberHeadphoneOverlayState(): HeadphoneOverlayState {
    val state = remember { HeadphoneOverlayState() }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val am = context.getSystemService(AudioManager::class.java) ?: return@DisposableEffect onDispose { }

        // Snapshot the devices *before* we register so we only react to
        // genuinely new additions.
        val known = mutableSetOf<Int>()
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).forEach { known += it.id }

        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                for (d in addedDevices) {
                    if (d.id in known) continue
                    known += d.id
                    if (d.isSink && d.type in HEADPHONE_TYPES) {
                        state.show(labelFor(d))
                    }
                }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                for (d in removedDevices) known -= d.id
            }
        }

        am.registerAudioDeviceCallback(callback, null)
        onDispose { am.unregisterAudioDeviceCallback(callback) }
    }

    return state
}

private val HEADPHONE_TYPES = setOf(
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER
)

private fun labelFor(d: AudioDeviceInfo): String {
    val product = d.productName?.toString()?.trim()
    if (!product.isNullOrBlank()) return product
    return when (d.type) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB device"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE"
        else -> "Audio device"
    }
}
