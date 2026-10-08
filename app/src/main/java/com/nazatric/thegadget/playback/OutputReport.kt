package com.nazatric.thegadget.playback

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat

data class OutputReport(
    val outputSampleRate: Int,
    val framesPerBuffer: Int,
    val devices: List<String>,
    val bluetooth: Boolean,
    val bitPerfect: Boolean,
    val note: String,
)

fun outputReport(context: Context, sourceRate: Int, sourceBitDepth: Int, lossless: Boolean): OutputReport {
    val audio = context.getSystemService(AudioManager::class.java)
    val rate = audio?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 0
    val frames = audio?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 0
    val devices = mutableListOf<String>()
    var bluetooth = false
    if (Build.VERSION.SDK_INT >= 23 && audio != null) {
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).forEach { device ->
            val name = device.productName?.toString().orEmpty().ifBlank { typeName(device.type) }
            if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
            ) {
                bluetooth = true
            }
            if (device.isSink) devices += name
        }
    }
    if (bluetoothName(context) != null) bluetooth = true
    val rateMatch = sourceRate > 0 && rate > 0 && sourceRate == rate
    val bitPerfect = lossless && rateMatch && !bluetooth && sourceBitDepth in 16..24
    val note = when {
        bluetooth -> "Bluetooth is compressing this path. Bit-perfect playback is not available here."
        sourceRate <= 0 -> "Source rate is unknown. The device mixer decides the output rate."
        rate <= 0 -> "This device did not report an output sample rate."
        !rateMatch -> "Device mixer is ${rate} Hz. Source is ${sourceRate} Hz, so Android will resample. Bit-perfect output is not available on this path."
        !lossless -> "Source is lossy. Playback will not invent detail that was never in the file."
        bitPerfect -> "Source rate matches the reported output rate and the file is lossless. Android can still process the stream; this is as close as this path gets, not a guarantee of an exclusive bit-perfect device."
        else -> "Output path reported. Exclusive bit-perfect mode is not exposed by this Android audio path."
    }
    return OutputReport(rate, frames, devices.distinct().take(4), bluetooth, bitPerfect, note)
}

private fun bluetoothName(context: Context): String? {
    if (Build.VERSION.SDK_INT < 31) return null
    if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        return null
    }
    return runCatching {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return null
        manager.adapter?.bondedDevices?.firstOrNull { it.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED }?.name
    }.getOrNull()
}

private fun typeName(type: Int): String = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired headphones"
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bluetooth"
    AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "usb"
    else -> "output"
}
