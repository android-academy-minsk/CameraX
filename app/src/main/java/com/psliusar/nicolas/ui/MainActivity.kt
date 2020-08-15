package com.psliusar.nicolas.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.psliusar.nicolas.R
import com.psliusar.nicolas.ui.camera.CameraFragment
import com.psliusar.nicolas.ui.home.HomeFragment
import com.psliusar.nicolas.utils.Permissioner
import com.psliusar.nicolas.utils.Permissionist
import com.psliusar.nicolas.utils.SingleModelFactory
import com.psliusar.nicolas.utils.showAlert
import kotlinx.android.synthetic.main.activity_main.*

/** Combination of all flags required to put activity below status bar */
private const val FLAGS_FULLSCREEN = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

class MainActivity : AppCompatActivity(), Permissionist {

    override val permissioner: Permissioner by lazy { Permissioner(this, ::showRationaleDialog) }

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = SingleModelFactory.get(this) {
            MainViewModel(applicationContext)
        }

        transparentStatusBar()

        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            toolbar.setPaddingRelative(0, insets.systemWindowInsetTop, 0, 0)
            insets
        }

        if (savedInstanceState == null) {
            // First run, show the menu
            supportFragmentManager
                .beginTransaction()
                .add(R.id.container, HomeFragment())
                .commit()
        }
    }

    fun showFragment(fragment: Fragment) {
        val manager = supportFragmentManager
        manager
            .beginTransaction()
            .replace(R.id.container, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissioner.onResult(permissions, grantResults)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (viewModel.registerKeyDown(keyCode)) {
            true
        } else {
            super.onKeyDown(keyCode, event)
        }
    }

    private fun showRationaleDialog(permissions: Collection<String>, retry: () -> Unit): Boolean {
        return viewModel.getPermissionRationaleText(permissions)
            ?.let {
                showAlert {
                    setMessage(it)
                    setPositiveButton(R.string.yes) { _, _ -> retry() }
                    setNegativeButton(R.string.no) { _, _ -> finish() }
                }
                true
            }
            ?: false
    }

    private fun transparentStatusBar() {
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or FLAGS_FULLSCREEN
    }
}
