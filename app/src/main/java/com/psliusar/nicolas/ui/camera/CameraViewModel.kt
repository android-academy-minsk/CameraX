package com.psliusar.nicolas.ui.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.util.Log
import android.view.Surface
import android.view.View
import android.widget.Toast
import androidx.camera.core.CameraX
import androidx.camera.core.ImageCapture
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LiveData
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ViewModel
import com.psliusar.nicolas.utils.PermissionDeniedException
import com.psliusar.nicolas.utils.Permissioner
import com.psliusar.nicolas.utils.SingleLiveEvent
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import java.io.File

private const val TAG = "CameraXApp"

class CameraViewModel(
    private val context: Context
) : ViewModel(), LifecycleObserver {

    val startCamera: LiveData<Unit>
        get() = _startCamera
    private val _startCamera = SingleLiveEvent<Unit>()

    val quit: LiveData<Unit>
        get() = _quit
    private val _quit = SingleLiveEvent<Unit>()

    var lensFacing = CameraX.LensFacing.BACK
        private set

    private val disposables = CompositeDisposable()

    @OnLifecycleEvent(value = Lifecycle.Event.ON_STOP)
    fun onStop() {
        disposables.clear()
    }

    fun init(permissioner: Permissioner) {
        permissioner
            .need(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                _startCamera.call()
            }, {
                when (it) {
                    is PermissionDeniedException -> {
                        Toast.makeText(context, "Required permissions not granted.", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "Permissions not granted by the user: $it")
                        _quit.call()
                    }
                    else -> throw RuntimeException("Unable to get permissions for camera", it)
                }
            })
            .let(disposables::add)
    }

    fun getViewFinderTransformationMatrix(viewFinder: View): Matrix? {
        val matrix = Matrix()

        // Compute the center of the view finder
        val centerX = viewFinder.width / 2f
        val centerY = viewFinder.height / 2f

        // Correct preview output to account for display rotation
        val rotationDegrees = when (viewFinder.display.rotation) {
            Surface.ROTATION_0 -> 0f
            Surface.ROTATION_90 -> 90f
            Surface.ROTATION_180 -> 180f
            Surface.ROTATION_270 -> 270f
            else -> return null
        }
        matrix.postRotate(-rotationDegrees, centerX, centerY)

        return matrix
    }

    fun takePicture(imageCapture: ImageCapture) {
        imageCapture.takePicture(
            File(getOutputDirectory(), "${System.currentTimeMillis()}.jpg"),
            object : ImageCapture.OnImageSavedListener {
                override fun onImageSaved(file: File) {
                    val msg = "Photo capture succeeded: ${file.absolutePath}"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }

                override fun onError(error: ImageCapture.UseCaseError, message: String, exc: Throwable?) {
                    val reason = when (error) {
                        ImageCapture.UseCaseError.UNKNOWN_ERROR -> "unknown error"
                        ImageCapture.UseCaseError.FILE_IO_ERROR -> "unable to save file"
                    }
                    val msg = "Photo capture failed: $reason"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    Log.e(TAG, msg)
                    exc?.printStackTrace()
                }
            }
        )
    }

    @SuppressLint("RestrictedApi")
    fun switchToNextLensFacing(): Boolean {
        val switchTo = when (lensFacing) {
            CameraX.LensFacing.FRONT -> CameraX.LensFacing.BACK
            CameraX.LensFacing.BACK -> CameraX.LensFacing.FRONT
        }
        return try {
            // Only bind use cases if we can query a camera with this orientation
            CameraX.getCameraWithLensFacing(switchTo)
            lensFacing = switchTo
            true
        } catch (exc: Exception) {
            false
        }
    }

    fun getUseCaseFactory(): UseCaseFactory {
        return UseCaseFactory()
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