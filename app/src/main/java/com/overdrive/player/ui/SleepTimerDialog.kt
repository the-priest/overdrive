package com.overdrive.player.ui

import android.content.Context
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.overdrive.player.R
import com.overdrive.player.player.SleepTimer
import com.overdrive.player.util.formatDuration

/** Picker for [SleepTimer]: off, a fixed number of minutes, or end of track. */
object SleepTimerDialog {

    private val MINUTES = intArrayOf(5, 10, 15, 30, 45, 60, 90)

    fun show(context: Context) {
        val labels = ArrayList<String>()
        labels.add(context.getString(R.string.sleep_off))
        MINUTES.forEach { minutes ->
            labels.add(
                if (minutes == 60) context.getString(R.string.sleep_hour)
                else context.getString(R.string.sleep_minutes, minutes)
            )
        }
        labels.add(context.getString(R.string.sleep_end_of_track))

        val checked = when (SleepTimer.state.value) {
            SleepTimer.State.Off -> 0
            SleepTimer.State.EndOfTrack -> labels.lastIndex
            is SleepTimer.State.Counting -> -1
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.sleep_timer_title)
            .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, which ->
                when (which) {
                    0 -> {
                        SleepTimer.cancel()
                        toast(context, context.getString(R.string.sleep_cancelled))
                    }
                    labels.lastIndex -> {
                        SleepTimer.stopAtEndOfTrack()
                        toast(context, context.getString(R.string.sleep_set_end_of_track))
                    }
                    else -> {
                        val ms = MINUTES[which - 1] * 60_000L
                        SleepTimer.start(ms)
                        toast(context, context.getString(R.string.sleep_set, formatDuration(ms)))
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toast(context: Context, text: String) =
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
