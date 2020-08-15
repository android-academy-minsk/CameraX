package com.psliusar.nicolas.ui.camera

import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.psliusar.nicolas.R
import com.psliusar.nicolas.camera.LuminosityAnalyzer
import com.psliusar.nicolas.ui.MainViewModel
import com.psliusar.nicolas.utils.Permissionist
import com.psliusar.nicolas.utils.SingleModelFactory
import com.psliusar.nicolas.utils.initCamera
import com.psliusar.nicolas.utils.requireParent
import com.psliusar.nicolas.utils.simulateClick
import kotlinx.android.synthetic.main.fragment_camera.*

private const val TAG = "CameraFragment"

class CameraFragment : Fragment(R.layout.fragment_camera) {

    private lateinit var mainViewModel: MainViewModel
    private lateinit var viewModel: CameraViewModel

    private lateinit var previewUseCase: Preview
    private lateinit var captureUseCase: ImageCapture
    private lateinit var analysisUseCase: ImageAnalysis

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)

        viewModel = SingleModelFactory.get(this) {
            CameraViewModel(requireContext().applicationContext)
        }
        lifecycle.addObserver(viewModel)

        viewModel.quit.observe(this, Observer {
            requireActivity().finishAfterTransition()
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        switchCamera.setOnClickListener {
            viewModel.switchToNextLensFacing()
        }

        viewModel.startCamera.observe(viewLifecycleOwner, Observer {
            viewFinder.post(::startCamera)
        })

        mainViewModel = SingleModelFactory.getFromActivity(this)
        mainViewModel.onKeyDown(MainViewModel.KeyCode.SHUTTER).observe(viewLifecycleOwner, Observer {
            shutter.simulateClick()
        })
        viewModel.lensFacing.observe(viewLifecycleOwner, Observer {
            startCamera()
        })

        viewModel.init(requireParent<Permissionist>().permissioner)
    }

    private fun startCamera() {
        initCamera(requireContext()) {
            bindCameraUseCases(it)
        }
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        // Create configuration object for the viewfinder use case
        previewUseCase = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(viewFinder.createSurfaceProvider())
            }

        captureUseCase = ImageCapture.Builder()
            // We don't set a resolution for image capture; instead, we
            // select a capture mode which will infer the appropriate
            // resolution based on aspect ration and requested mode
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
        shutter.setOnClickListener {
            viewModel.takePicture(captureUseCase)
        }

        val analyzer = LuminosityAnalyzer().apply {
            addListener {
                Log.d(TAG, "Average luminosity: $it. Frames per second: ${"%.01f".format(framesPerSecond)}")
            }
        }
        analysisUseCase = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(AsyncTask.THREAD_POOL_EXECUTOR, analyzer)
            }

        try {
            // Unbind use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to lifecycle
            val camera = cameraProvider.bindToLifecycle(
                viewLifecycleOwner,
                CameraSelector.Builder().requireLensFacing(viewModel.lensFacing.value!!).build(),
                previewUseCase,
                captureUseCase,
                analysisUseCase
            )
        } catch (exc: IllegalStateException) {
            Log.e(TAG, "Use case binding failed. This must be running on main thread.", exc)
        }
    }
}
