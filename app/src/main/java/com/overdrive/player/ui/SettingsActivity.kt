package com.overdrive.player.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.overdrive.player.BuildConfig
import com.overdrive.player.OverdriveApp
import com.overdrive.player.R
import com.overdrive.player.databinding.ActivitySettingsBinding
import com.overdrive.player.util.ThemeHelper

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val prefs by lazy { OverdriveApp.from(this).prefs }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.switchPauseDisconnect.isChecked = prefs.pauseOnDisconnect
        binding.switchPauseDisconnect.setOnCheckedChangeListener { _, value ->
            prefs.pauseOnDisconnect = value
        }

        binding.switchShuffleDefault.isChecked = prefs.shuffleDefault
        binding.switchShuffleDefault.setOnCheckedChangeListener { _, value ->
            prefs.shuffleDefault = value
        }

        updateMinLengthLabel()
        binding.rowMinLength.setOnClickListener { showMinLengthDialog() }

        when (prefs.accent) {
            1 -> binding.accentAmber.isChecked = true
            2 -> binding.accentBlue.isChecked = true
            else -> binding.accentRed.isChecked = true
        }
        binding.groupAccent.setOnCheckedChangeListener { _, checkedId ->
            val index = when (checkedId) {
                R.id.accentAmber -> 1
                R.id.accentBlue -> 2
                else -> 0
            }
            if (prefs.accent != index) {
                prefs.accent = index
                setResult(RESULT_FIRST_USER)
            }
        }

        binding.rowEqualizer.setOnClickListener {
            startActivity(Intent(this, EqualizerActivity::class.java))
        }

        binding.version.text = "${getString(R.string.version)} ${BuildConfig.VERSION_NAME}"

        binding.rescanButton.setOnClickListener { setResult(RESULT_OK) }
    }

    private fun updateMinLengthLabel() {
        binding.minLengthValue.text = when (prefs.minDurationSec) {
            0 -> "Off"
            30 -> "30 s"
            60 -> "1 min"
            120 -> "2 min"
            300 -> "5 min"
            else -> "${prefs.minDurationSec} s"
        }
    }

    private fun showMinLengthDialog() {
        val options = arrayOf("Off", "30 seconds", "1 minute", "2 minutes", "5 minutes")
        val values = intArrayOf(0, 30, 60, 120, 300)
        val current = values.indexOf(prefs.minDurationSec).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.min_length)
            .setSingleChoiceItems(options, current) { dialog, which ->
                prefs.minDurationSec = values[which]
                updateMinLengthLabel()
                setResult(RESULT_OK)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
