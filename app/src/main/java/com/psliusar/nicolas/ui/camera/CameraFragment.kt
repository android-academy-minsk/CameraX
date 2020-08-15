package com.psliusar.nicolas.ui.camera

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.psliusar.nicolas.R
import com.psliusar.nicolas.ui.MainViewModel
import com.psliusar.nicolas.utils.Permissionist
import com.psliusar.nicolas.utils.SingleModelFactory
import com.psliusar.nicolas.utils.requireParent
import com.psliusar.nicolas.utils.simulateClick
import kotlinx.android.synthetic.main.fragment_camera.*
import java.util.concurrent.ExecutionException

private const val TAG = "NicolasCamera"

class CameraFragment : Fragment() {

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_camera, container, false)

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
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider = try {
                cameraProviderFuture.get()
            } catch (e: ExecutionException) {
                throw IllegalStateException("Camera initialization failed.", e.cause!!)
            }
            // Build and bind the camera use cases
            bindCameraUseCases(cameraProvider)
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        val factory = viewModel.getUseCaseFactory()

        previewUseCase = factory.getPreviewUseCase(viewFinder)

        captureUseCase = factory.getCaptureUseCase()
        shutter.setOnClickListener {
            viewModel.takePicture(captureUseCase)
        }

        val analyzer = factory.createLuminosityAnalyzer().apply {
            addListener {
                Log.d(TAG, "Average luminosity: $it. Frames per second: ${"%.01f".format(framesPerSecond)}")
            }
        }
        analysisUseCase = factory.getAnalyzerUseCase(analyzer)

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
