package com.psliusar.nicolas.utils

import android.app.Activity
import android.view.View
import androidx.appcompat.app.AlertDialog

private const val ANIMATION_FAST_MILLIS = 300L

/**
 * Simulate a button click, including a small delay while it is being pressed to trigger the
 * appropriate animations.
 */
fun View.simulateClick(delay: Long = ANIMATION_FAST_MILLIS) {
    performClick()
    isPressed = true
    invalidate()
    postDelayed({
        invalidate()
        isPressed = false
    }, delay)
}

fun Activity.showAlert(setup: AlertDialog.Builder.() -> Unit) {
    val builder = AlertDialog.Builder(this)
    setup(builder)
    builder.show()
}