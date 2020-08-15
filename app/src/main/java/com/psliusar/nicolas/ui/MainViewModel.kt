package com.psliusar.nicolas.ui

import android.Manifest
import android.content.Context
import android.view.KeyEvent
import androidx.lifecycle.LiveData
import androidx.lifecycle.LiveDataReactiveStreams
import androidx.lifecycle.ViewModel
import com.psliusar.nicolas.R
import io.reactivex.BackpressureStrategy
import io.reactivex.subjects.PublishSubject

class MainViewModel(private val context: Context) : ViewModel() {

    private val keyDownPublisher = PublishSubject.create<KeyCode>()

    fun registerKeyDown(keyCode: Int): Boolean {
        if (!keyDownPublisher.hasObservers()) {
            return false
        }
        return getKeyCode(keyCode)?.let(keyDownPublisher::onNext) != null
    }

    fun onKeyDown(keyCode: KeyCode): LiveData<KeyCode> = LiveDataReactiveStreams.fromPublisher(
        keyDownPublisher
            .filter { it == keyCode }
            .toFlowable(BackpressureStrategy.LATEST)
    )

    fun getPermissionRationaleText(permissions: Collection<String>): String? {
        if (permissions.size > 1) {
            val perms = permissions.mapNotNull(::getPermissionName).joinToString(",\n") { " - $it" }
            return context.getString(R.string.need_permissions, perms)
        }

        return when {
            permissions.contains(Manifest.permission.CAMERA) -> R.string.need_camera_permission
            else -> null
        }?.let(context::getString)
    }

    private fun getPermissionName(permission: String): String? = when (permission) {
        Manifest.permission.CAMERA -> R.string.permission_camera
        else -> null
    }?.let(context::getString)

    private fun getKeyCode(keyCode: Int): KeyCode? = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_DOWN -> KeyCode.SHUTTER
        else -> null
    }

    enum class KeyCode {
        SHUTTER
    }
}