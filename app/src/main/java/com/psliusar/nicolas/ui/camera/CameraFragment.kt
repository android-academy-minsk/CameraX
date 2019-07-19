package com.psliusar.nicolas.ui.camera

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.CameraX
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.psliusar.nicolas.R
import com.psliusar.nicolas.ui.MainViewModel
import com.psliusar.nicolas.utils.Permissionist
import com.psliusar.nicolas.utils.SingleModelFactory
import com.psliusar.nicolas.utils.requireParent
import com.psliusar.nicolas.utils.simulateClick
import kotlinx.android.synthetic.main.fragment_camera.*

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

        // Every time the provided texture view changes, recompute layout
        viewFinder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTransform()
        }

        switchCamera.setOnClickListener {
            if (viewModel.switchToNextLensFacing()) {
                startCamera()
            }
        }

        viewModel.startCamera.observe(viewLifecycleOwner, Observer {
            viewFinder.post(::startCamera)
        })

        mainViewModel = SingleModelFactory.getFromActivity(this)
        mainViewModel.onKeyDown(MainViewModel.KeyCode.SHUTTER).observe(this, Observer {
            shutter.simulateClick()
        })

        viewModel.init(requireParent<Permissionist>().permissioner)
    }

    private fun startCamera() {
        CameraX.unbindAll()

        val factory = viewModel.getUseCaseFactory()
        val lensFacing = viewModel.lensFacing

        previewUseCase = factory.getPreviewUseCase(viewFinder, lensFacing) {
            updateTransform()
        }

        captureUseCase = factory.getCaptureUseCase(lensFacing)
        shutter.setOnClickListener {
            viewModel.takePicture(captureUseCase)
        }

        val analyzer = factory.createLuminosityAnalyzer().apply {
            addListener {
                Log.d(TAG, "Average luminosity: $it. Frames per second: ${"%.01f".format(framesPerSecond)}")
            }
        }
        analysisUseCase = factory.getAnalyzerUseCase(analyzer, lensFacing)

        // Bind use cases to lifecycle
        CameraX.bindToLifecycle(
            viewLifecycleOwner,
            previewUseCase,
            captureUseCase,
            analysisUseCase
        )
    }

    private fun updateTransform() {
        viewModel.getViewFinderTransformationMatrix(viewFinder)
            ?.let(viewFinder::setTransform)
    }
}
