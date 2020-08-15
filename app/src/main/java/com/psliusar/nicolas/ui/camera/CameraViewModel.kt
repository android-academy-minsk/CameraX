package com.psliusar.nicolas.ui.camera

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ViewModel
import com.psliusar.nicolas.utils.Permissioner
import com.psliusar.nicolas.utils.SingleLiveEvent
import com.psliusar.nicolas.utils.waitForCameraPermission
import io.reactivex.disposables.CompositeDisposable
import java.io.File

private const val TAG = "CameraViewModel"

class CameraViewModel(
    private val context: Context
) : ViewModel(), LifecycleObserver {

    private val _startCamera = SingleLiveEvent<Unit>()
    val startCamera: LiveData<Unit> = _startCamera

    private val _quit = SingleLiveEvent<Unit>()
    val quit: LiveData<Unit> = _quit

    private val _lensFacing = MutableLiveData(CameraSelector.LENS_FACING_BACK)
    val lensFacing: LiveData<Int> = _lensFacing

    private val disposables = CompositeDisposable()

    @OnLifecycleEvent(value = Lifecycle.Event.ON_STOP)
    fun onStop() {
        disposables.clear()
    }

    fun init(permissioner: Permissioner) {
        waitForCameraPermission(
            context,
            permissioner,
            { _startCamera.call() },
            { _quit.call() }
        ).let(disposables::add)
    }

    fun takePicture(imageCapture: ImageCapture) {
        val file = File(getOutputDirectory(), "${System.currentTimeMillis()}.jpg")
        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
        // Set up image capture listener, which is triggered after photo has been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${file.absolutePath}"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }

                override fun onError(exception: ImageCaptureException) {
                    val reason = when (exception.imageCaptureError) {
                        ImageCapture.ERROR_UNKNOWN -> "unknown error"
                        ImageCapture.ERROR_FILE_IO -> "unable to save file"
                        ImageCapture.ERROR_CAPTURE_FAILED -> "capture failed"
                        ImageCapture.ERROR_CAMERA_CLOSED -> "camera closed"
                        ImageCapture.ERROR_INVALID_CAMERA -> "invalid camera"
                        else -> "unknown error"
                    }
                    val msg = "Photo capture failed: $reason"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    Log.e(TAG, msg)
                    exception.printStackTrace()
                }
            }
        )
    }

    fun switchToNextLensFacing() {
        _lensFacing.value = when (_lensFacing.value) {
            CameraSelector.LENS_FACING_FRONT -> CameraSelector.LENS_FACING_BACK
            CameraSelector.LENS_FACING_BACK -> CameraSelector.LENS_FACING_FRONT
            else -> throw IllegalStateException("Unexpected state, lensFacing=${_lensFacing.value}")
        }
    }

    /** Use external media if it is available, our app's file directory otherwise */
    private fun getOutputDirectory(): File {
        return context.externalMediaDirs
            .firstOrNull()
            ?.let {
                File(it, "photos").apply { mkdirs() }
            }
            ?.takeIf { it.exists() }
            ?: context.filesDir
    }
}