package com.overdrive.player.util

import android.content.Context
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.overdrive.player.OverdriveApp
import com.overdrive.player.R

/**
 * Applies the user's chosen accent theme. Must be called before
 * `setContentView` so the activity is created with the right palette.
 */
object ThemeHelper {

    private val THEMES = intArrayOf(
        R.style.Theme_Overdrive,
        R.style.Theme_Overdrive_Amber,
        R.style.Theme_Overdrive_Blue
    )

    fun apply(context: Context) {
        val index = OverdriveApp.from(context).prefs.accent.coerceIn(0, THEMES.size - 1)
        context.setTheme(THEMES[index])
    }

    @ColorRes
    fun accentColorRes(context: Context): Int =
        when (OverdriveApp.from(context).prefs.accent) {
            1 -> R.color.rock_amber
            2 -> R.color.rock_steel
            else -> R.color.rock_red
        }

    fun accentColor(context: Context): Int =
        ContextCompat.getColor(context, accentColorRes(context))
}
