package com.overdrive.player.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.overdrive.player.BuildConfig
import com.overdrive.player.OverdriveApp
import com.overdrive.player.R
import com.overdrive.player.databinding.ActivitySettingsBinding
import com.overdrive.player.util.ThemeHelper
import com.overdrive.player.util.applySystemBarPadding
import com.overdrive.player.util.enableDarkEdgeToEdge

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val prefs by lazy { OverdriveApp.from(this).prefs }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.apply(this)
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding(top = true, bottom = true)

        binding.toolbar.setNavigationOnClickListener { finish() }

        // The playback service listens for this one and applies it live.
        binding.switchPauseDisconnect.isChecked = prefs.pauseOnDisconnect
        binding.switchPauseDisconnect.setOnCheckedChangeListener { _, value ->
            prefs.pauseOnDisconnect = value
        }

        binding.switchShuffleDefault.isChecked = prefs.shuffleDefault
        binding.switchShuffleDefault.setOnCheckedChangeListener { _, value ->
            prefs.shuffleDefault = value
        }

        binding.switchResume.isChecked = prefs.resumeLastSession
        binding.switchResume.setOnCheckedChangeListener { _, value ->
            prefs.resumeLastSession = value
        }

        updateMinLengthLabel()
        binding.rowMinLength.setOnClickListener { showMinLengthDialog() }

        when (ThemeHelper.accentIndex(this)) {
            1 -> binding.accentAmber.isChecked = true
            2 -> binding.accentBlue.isChecked = true
            3 -> binding.accentToxic.isChecked = true
            else -> binding.accentRed.isChecked = true
        }
        binding.groupAccent.setOnCheckedChangeListener { _, checkedId ->
            val index = when (checkedId) {
                R.id.accentAmber -> 1
                R.id.accentBlue -> 2
                R.id.accentToxic -> 3
                else -> 0
            }
            if (prefs.accent != index) {
                prefs.accent = index
                // Every other screen notices the change in onResume.
                recreate()
            }
        }

        binding.rowEqualizer.setOnClickListener {
            startActivity(Intent(this, EqualizerActivity::class.java))
        }

        binding.version.text = getString(R.string.version, BuildConfig.VERSION_NAME)

        binding.rescanButton.setOnClickListener {
            setResult(RESULT_RESCAN)
            finish()
        }
    }

    private fun minLengthLabel(seconds: Int): String = when (seconds) {
        0 -> getString(R.string.off)
        30 -> "30 s"
        60 -> "1 min"
        120 -> "2 min"
        300 -> "5 min"
        else -> "$seconds s"
    }

    private fun updateMinLengthLabel() {
        binding.minLengthValue.text = minLengthLabel(prefs.minDurationSec)
    }

    private fun showMinLengthDialog() {
        val values = intArrayOf(0, 30, 60, 120, 300)
        val labels = values.map { minLengthLabel(it) }.toTypedArray()
        val current = values.indexOf(prefs.minDurationSec).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.min_length)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                // The library reloads itself when this preference changes.
                prefs.minDurationSec = values[which]
                updateMinLengthLabel()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        const val RESULT_RESCAN = Activity.RESULT_FIRST_USER + 1
    }
}
