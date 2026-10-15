package com.overdrive.player.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.enableEdgeToEdge
import androidx.core.view.updatePadding

/**
 * The app draws edge-to-edge (mandatory from Android 15 when targeting SDK 35+).
 * This pads a view by the system bar / display cutout insets on the requested
 * sides, on top of whatever padding it already declares in XML.
 */
fun View.applySystemBarPadding(top: Boolean = false, bottom: Boolean = false) {
    val initialTop = paddingTop
    val initialBottom = paddingBottom
    val initialLeft = paddingLeft
    val initialRight = paddingRight
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val bars = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.updatePadding(
            left = initialLeft + bars.left,
            right = initialRight + bars.right,
            top = initialTop + if (top) bars.top else 0,
            bottom = initialBottom + if (bottom) bars.bottom else 0
        )
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}

/**
 * Edge-to-edge with light system-bar icons. The app is always dark, so the
 * default "follow the system theme" choice would draw dark icons on a dark
 * background whenever the phone is in light mode.
 */
fun androidx.activity.ComponentActivity.enableDarkEdgeToEdge() {
    val transparent = android.graphics.Color.TRANSPARENT
    enableEdgeToEdge(
        statusBarStyle = androidx.activity.SystemBarStyle.dark(transparent),
        navigationBarStyle = androidx.activity.SystemBarStyle.dark(transparent)
    )
}
