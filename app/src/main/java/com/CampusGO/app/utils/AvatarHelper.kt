package com.CampusGO.app.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.CampusGO.app.R

object AvatarHelper {
    /**
     * Set user avatar in a TextView.
     * If the user has a custom base64 encoded picture, it is decoded and set as background.
     * Otherwise, falls back to initials inside a circular background.
     */
    fun setAvatar(textView: TextView, name: String, base64Str: String?) {
        if (!base64Str.isNullOrEmpty()) {
            try {
                val decodedString = Base64.decode(base64Str, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                if (bitmap != null) {
                    val context = textView.context
                    val circularDrawable = RoundedBitmapDrawableFactory.create(context.resources, bitmap)
                    circularDrawable.isCircular = true
                    textView.background = circularDrawable
                    textView.text = "" // clear initials text
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // Fallback to text initials with bg_avatar background
        textView.setBackgroundResource(R.drawable.bg_avatar)
        textView.text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    }

    /**
     * Set user avatar in an ImageView.
     * If the user has a custom base64 encoded picture, it is decoded and set as src.
     * Otherwise, falls back to initials inside a fallback TextView.
     */
    fun setAvatar(imageView: ImageView, name: String, base64Str: String?, fallbackTextView: TextView? = null) {
        if (!base64Str.isNullOrEmpty()) {
            try {
                val decodedString = Base64.decode(base64Str, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                if (bitmap != null) {
                    val context = imageView.context
                    val circularDrawable = RoundedBitmapDrawableFactory.create(context.resources, bitmap)
                    circularDrawable.isCircular = true
                    imageView.setImageDrawable(circularDrawable)
                    imageView.visibility = View.VISIBLE
                    fallbackTextView?.visibility = View.GONE
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Fallback to text initials in fallbackTextView if provided
        if (fallbackTextView != null) {
            imageView.visibility = View.GONE
            fallbackTextView.visibility = View.VISIBLE
            fallbackTextView.setBackgroundResource(R.drawable.bg_avatar)
            fallbackTextView.text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        } else {
            imageView.setImageResource(R.drawable.ic_profile)
        }
    }
}
