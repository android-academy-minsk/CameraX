package com.psliusar.nicolas.ui.camera

import android.os.Handler
import android.os.HandlerThread
import android.util.Rational
import android.util.Size
import android.view.TextureView
import androidx.camera.core.CameraX
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysisConfig
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureConfig
import androidx.camera.core.Preview
import androidx.camera.core.PreviewConfig
import com.psliusar.nicolas.camera.LuminosityAnalyzer

/**
 * Use cases for camera
 */
class UseCaseFactory {

    fun getPreviewUseCase(
        textureView: TextureView,
        lensFacing: CameraX.LensFacing,
        onUpdate: () -> Unit
    ): Preview {
        // Create configuration object for the viewfinder use case
        val previewConfig = PreviewConfig.Builder()
            .setTargetAspectRatio(Rational(1, 1))
            .setLensFacing(lensFacing)
            .build()

        // Build the viewfinder use case
        val preview = Preview(previewConfig)

        // Every time the viewfinder is updated, recompute layout
        preview.setOnPreviewOutputUpdateListener {
            textureView.surfaceTexture = it.surfaceTexture
            onUpdate()
        }

        return preview
    }

    fun getCaptureUseCase(lensFacing: CameraX.LensFacing): ImageCapture {
        // Create configuration object for the image capture use case
        val imageCaptureConfig = ImageCaptureConfig.Builder()
            .setTargetAspectRatio(Rational(1, 1))
            .setLensFacing(lensFacing)
            // We don't set a resolution for image capture; instead, we
            // select a capture mode which will infer the appropriate
            // resolution based on aspect ration and requested mode
            .setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
            .build()

        // Build the image capture use case
        return ImageCapture(imageCaptureConfig)
    }

    fun createLuminosityAnalyzer(): LuminosityAnalyzer {
        return LuminosityAnalyzer()
    }

    fun getAnalyzerUseCase(analyzer: ImageAnalysis.Analyzer, lensFacing: CameraX.LensFacing): ImageAnalysis {
        // Setup image analysis pipeline that computes average pixel luminance
        val analyzerConfig = ImageAnalysisConfig.Builder().apply {
            // Use a worker thread for image analysis to prevent glitches
            val analyzerThread = HandlerThread("LuminosityAnalysis").apply { start() }
            val handler = Handler(analyzerThread.looper)
            setCallbackHandler(handler)
            setLensFacing(lensFacing)
            // In our analysis, we care more about the latest image than
            // analyzing *every* image
            setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
            setTargetResolution(Size(300, 300))
        }.build()

        // Build the image analysis use case and instantiate our analyzer

        return ImageAnalysis(analyzerConfig).apply {
            this.analyzer = analyzer
        }
    }
}