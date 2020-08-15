package com.psliusar.nicolas.utils

import android.Manifest
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import java.util.concurrent.ExecutionException

fun waitForCameraPermission(
    context: Context,
    permissioner: Permissioner,
    onAllowed: () -> Unit,
    onDenied: () -> Unit
): Disposable {
    return permissioner
        .need(Manifest.permission.CAMERA)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe({
            onAllowed()
        }, {
            when (it) {
                is PermissionDeniedException -> {
                    Toast.makeText(context, "Required permissions not granted.", Toast.LENGTH_SHORT).show()
                    Log.d("Camera request", "Permissions not granted by the user: $it")
                    onDenied()
                }
                else -> throw RuntimeException("Unable to get permissions for camera", it)
            }
        })
}

fun initCamera(context: Context, onCameraReady: (ProcessCameraProvider) -> Unit) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener(Runnable {
        val cameraProvider = try {
            cameraProviderFuture.get()
        } catch (e: ExecutionException) {
            throw IllegalStateException("Camera initialization failed.", e.cause!!)
        }
        onCameraReady(cameraProvider)
    }, ContextCompat.getMainExecutor(context))
}