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
