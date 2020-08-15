package com.psliusar.nicolas.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.psliusar.nicolas.R
import com.psliusar.nicolas.ui.MainActivity
import com.psliusar.nicolas.ui.camera.CameraFragment
import com.psliusar.nicolas.ui.translate.TranslateFragment
import com.psliusar.nicolas.utils.requireParent
import kotlinx.android.synthetic.main.fragment_home.*

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var activity: MainActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = requireParent()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        showCamera.setOnClickListener {
            activity.showFragment(CameraFragment())
        }

        showTranslate.setOnClickListener {
            activity.showFragment(TranslateFragment())
        }
    }
}