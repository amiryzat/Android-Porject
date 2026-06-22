package com.CampusGO.app

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object InsetsHelper {
    fun applyTopInsetPadding(view: View) {
        val initialLeft   = view.paddingLeft
        val initialTop    = view.paddingTop
        val initialRight  = view.paddingRight
        val initialBottom = view.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val cutout     = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val topInset   = maxOf(statusBars.top, cutout.top)
            v.setPadding(initialLeft, initialTop + topInset, initialRight, initialBottom)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }
}
