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
        R.style.Theme_Overdrive_Blue,
        R.style.Theme_Overdrive_Toxic
    )

    const val ACCENT_COUNT = 4

    fun accentIndex(context: Context): Int =
        OverdriveApp.from(context).prefs.accent.coerceIn(0, THEMES.size - 1)

    /** Applies the theme and returns the accent index it was created with. */
    fun apply(context: Context): Int {
        val index = accentIndex(context)
        context.setTheme(THEMES[index])
        return index
    }

    @ColorRes
    fun accentColorRes(context: Context): Int =
        when (accentIndex(context)) {
            1 -> R.color.rock_amber
            2 -> R.color.rock_steel
            3 -> R.color.rock_toxic
            else -> R.color.rock_red
        }

    fun accentColor(context: Context): Int =
        ContextCompat.getColor(context, accentColorRes(context))
}
