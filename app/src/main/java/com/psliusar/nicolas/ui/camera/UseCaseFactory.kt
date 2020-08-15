package com.psliusar.nicolas.ui.camera

import android.os.AsyncTask
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import com.psliusar.nicolas.camera.LuminosityAnalyzer

/**
 * Use cases for camera
 */
class UseCaseFactory {

    fun getPreviewUseCase(previewView: PreviewView): Preview {
        // Create configuration object for the viewfinder use case
        return Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.createSurfaceProvider())
        }
    }

    fun getCaptureUseCase(): ImageCapture {
        // Build the image capture use case
        return ImageCapture.Builder()
            // We don't set a resolution for image capture; instead, we
            // select a capture mode which will infer the appropriate
            // resolution based on aspect ration and requested mode
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    fun createLuminosityAnalyzer(): LuminosityAnalyzer {
        return LuminosityAnalyzer()
    }

    fun getAnalyzerUseCase(analyzer: ImageAnalysis.Analyzer): ImageAnalysis {
        // Build the image analysis use case and instantiate our analyzer
        return ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(AsyncTask.THREAD_POOL_EXECUTOR, analyzer)
            }
    }
}