package com.CampusGO.app.util

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun Activity.handleKeyboardInsets(scrollView: View) {
    ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
        val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        view.setPadding(
            view.paddingLeft,
            view.paddingTop,
            view.paddingRight,
            maxOf(imeBottom, navBottom)
        )
        insets
    }
}

// Applies the status bar + display cutout top inset to a header container as padding,
// making it grow downward so its content always renders below the camera cutout.
// Captures the initial XML paddingTop before the listener fires to avoid compounding
// padding on each activity recreation.
fun View.applyStatusBarInsets() {
    val initialPaddingTop = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { target, insets ->
        val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
        val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        val topInset = maxOf(statusBars.top, cutout.top)
        target.setPadding(
            target.paddingLeft,
            initialPaddingTop + topInset,
            target.paddingRight,
            target.paddingBottom
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
