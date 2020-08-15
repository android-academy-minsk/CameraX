package com.psliusar.nicolas.ui.translate

import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.AsyncTask
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.psliusar.nicolas.R
import com.psliusar.nicolas.camera.TextAnalyzer
import com.psliusar.nicolas.utils.Permissionist
import com.psliusar.nicolas.utils.SingleModelFactory
import com.psliusar.nicolas.utils.initCamera
import com.psliusar.nicolas.utils.requireParent
import kotlinx.android.synthetic.main.fragment_translate.*
import java.util.concurrent.Executor

private const val TAG = "TranslateFragment"

/**
 * Based on an example from https://github.com/googlesamples/mlkit/tree/master/android/translate-showcase
 */
class TranslateFragment : Fragment(R.layout.fragment_translate) {

    /** Blocking camera and inference operations are performed using this executor. */
    private lateinit var cameraExecutor: Executor

    private lateinit var viewModel: TranslateViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = SingleModelFactory.get(this) {
            TranslateViewModel(requireContext().applicationContext)
        }
        lifecycle.addObserver(viewModel)

        viewModel.quit.observe(this, Observer {
            requireActivity().finishAfterTransition()
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize our background executor
        cameraExecutor = AsyncTask.THREAD_POOL_EXECUTOR

        // Get available language list and set up the target language spinner
        // with default selections.
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            viewModel.availableLanguages
        )

        targetLangSelector.adapter = adapter
        targetLangSelector.setSelection(adapter.getPosition(
            Language(
                "en"
            )
        ))
        targetLangSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                viewModel.targetLang.value = adapter.getItem(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>) { }
        }

        overlay.apply {
            setZOrderOnTop(true)
            holder.setFormat(PixelFormat.TRANSPARENT)
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceChanged(
                    holder: SurfaceHolder?,
                    format: Int,
                    width: Int,
                    height: Int
                ) {
                }

                override fun surfaceDestroyed(holder: SurfaceHolder?) {}

                override fun surfaceCreated(holder: SurfaceHolder?) {
                    holder?.let {
                        drawOverlay(it, DESIRED_HEIGHT_CROP_PERCENT, DESIRED_WIDTH_CROP_PERCENT)
                    }
                }
            })
        }

        viewModel.sourceLang.observe(viewLifecycleOwner, Observer { sourceLang.text = it.displayName })
        viewModel.translatedText.observe(viewLifecycleOwner, Observer { result ->
            when (result) {
                is TranslationResult.Success -> translatedText.text = result.result
                is TranslationResult.Error -> translatedText.error = result.error?.localizedMessage
            }
        })
        viewModel.modelDownloading.observe(viewLifecycleOwner, Observer { isDownloading ->
            val visibility = if (isDownloading) View.VISIBLE else View.INVISIBLE
            progressBar.visibility = visibility
            progressText.visibility = visibility
        })
        viewModel.startCamera.observe(viewLifecycleOwner, Observer {
            viewFinder.post(::startCamera)
        })

        viewModel.init(requireParent<Permissionist>().permissioner, cameraExecutor)
    }

    private fun startCamera() {
        initCamera(requireContext()) {
            bindCameraUseCases(it)
        }
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        val screenAspectRatio = viewModel.aspectRatio(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")

        val rotation = viewFinder.display.rotation

        val previewUseCase = Preview.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(rotation)
            .build()

        // Build the image analysis use case and instantiate our analyzer
        val analyzer = TextAnalyzer(
            requireContext(),
            lifecycle,
            cameraExecutor,
            viewModel.sourceText,
            viewModel.imageCropPercentages
        )
        val analysisUseCase = ImageAnalysis.Builder()
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, analyzer)
            }

        viewModel.sourceText.observe(viewLifecycleOwner, Observer {
            sourceText.text = it
        })
        viewModel.imageCropPercentages.observe(viewLifecycleOwner, Observer {
            drawOverlay(overlay.holder, it.first, it.second)
        })

        try {
            // Unbind use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to camera
            val camera = cameraProvider.bindToLifecycle(
                viewLifecycleOwner,
                // Select back camera since text detection does not work with front camera
                CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build(),
                previewUseCase,
                analysisUseCase
            )
            previewUseCase.setSurfaceProvider(viewFinder.createSurfaceProvider())
        } catch (exc: IllegalStateException) {
            Log.e(TAG, "Use case binding failed. This must be running on main thread.", exc)
        }
    }

    private fun drawOverlay(holder: SurfaceHolder, heightCropPercent: Int, widthCropPercent: Int) {
        val canvas = holder.lockCanvas()
        val bgPaint = Paint()
        bgPaint.alpha = 140
        canvas.drawPaint(bgPaint)

        val rectPaint = Paint()
        rectPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        rectPaint.style = Paint.Style.FILL
        rectPaint.color = Color.WHITE

        val outlinePaint = Paint()
        outlinePaint.style = Paint.Style.STROKE
        outlinePaint.color = Color.WHITE
        outlinePaint.strokeWidth = 4f

        val surfaceWidth = holder.surfaceFrame.width()
        val surfaceHeight = holder.surfaceFrame.height()

        val cornerRadius = 25f
        // Set rect centered in frame
        val rectTop = surfaceHeight * heightCropPercent / 2 / 100f
        val rectLeft = surfaceWidth * widthCropPercent / 2 / 100f
        val rectRight = surfaceWidth * (1 - widthCropPercent / 2 / 100f)
        val rectBottom = surfaceHeight * (1 - heightCropPercent / 2 / 100f)
        val rect = RectF(rectLeft, rectTop, rectRight, rectBottom)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, rectPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, outlinePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        textPaint.color = Color.WHITE
        textPaint.textSize = 50F
        val overlayText = getString(R.string.overlay_help)
        val textBounds = Rect()
        textPaint.getTextBounds(overlayText, 0, overlayText.length, textBounds)
        val textX = (surfaceWidth - textBounds.width()) / 2f
        val textY = rectBottom + textBounds.height() + 15f // put text below rect and 15f padding
        canvas.drawText(getString(R.string.overlay_help), textX, textY, textPaint)
        holder.unlockCanvasAndPost(canvas)
    }

    companion object {
        // We only need to analyze the part of the image that has text, so we set crop percentages
        // to avoid analyze the entire image from the live camera feed.
        const val DESIRED_WIDTH_CROP_PERCENT = 8
        const val DESIRED_HEIGHT_CROP_PERCENT = 74
    }
}