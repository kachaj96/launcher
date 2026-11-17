package com.adrian.launcher.system

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

/** Flashlight toggle. No permission needed: CameraManager.setTorchMode works without CAMERA. */
class Torch(context: Context) {

    private val cameras = context.getSystemService(CameraManager::class.java)
    private var on = false

    private val cameraId: String? = runCatching {
        cameras.cameraIdList.firstOrNull { id ->
            val c = cameras.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }.getOrNull()

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) on = enabled
        }
    }

    init {
        runCatching { cameras.registerTorchCallback(callback, Handler(Looper.getMainLooper())) }
    }

    val available: Boolean get() = cameraId != null

    /** Returns the new state, or null if the flash is missing or busy (camera in use). */
    fun toggle(): Boolean? {
        val id = cameraId ?: return null
        return runCatching {
            cameras.setTorchMode(id, !on)
            on = !on
            on
        }.getOrNull()
    }

    fun release() {
        runCatching { cameras.unregisterTorchCallback(callback) }
    }
}
