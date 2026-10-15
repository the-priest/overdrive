package com.overdrive.player.ui

import android.content.res.ColorStateList
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.overdrive.player.OverdriveApp
import com.overdrive.player.R
import com.overdrive.player.databinding.ActivityEqualizerBinding
import com.overdrive.player.util.ThemeHelper
import java.util.Locale

/**
 * Graphic equalizer plus bass/virtualizer controls and the 200% volume boost.
 * All of it is driven by the device audio framework, applied only to our own
 * audio session.
 */
class EqualizerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEqualizerBinding
    private val app by lazy { OverdriveApp.from(this) }
    private val prefs by lazy { app.prefs }
    private val effects by lazy { app.audioEffects }

    private var probe: Equalizer? = null
    private var bandCount = 0
    private var bandMin = -1500
    private var bandMax = 1500
    private var presetNames: List<String> = emptyList()
    private val frequencies = ArrayList<Int>()

    private val bandSeekBars = ArrayList<SeekBar>()
    private val bandLabels = ArrayList<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityEqualizerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.eqSwitch.isChecked = prefs.equalizerEnabled

        readBandInfo()
        buildBands()
        buildPresets()
        setupBoostSeekBars()

        binding.eqSwitch.setOnCheckedChangeListener { _, checked ->
            effects.setEqualizerEnabled(checked)
            updateEnabledState()
        }
        binding.resetButton.setOnClickListener {
            effects.resetEqualizer(bandCount)
            refreshBandRows()
            if (presetNames.isNotEmpty()) binding.presetDropdown.setText("", false)
        }

        refreshBandRows()
        updateEnabledState()
    }

    override fun onDestroy() {
        super.onDestroy()
        probe?.release()
        probe = null
    }

    // --- equalizer bands ----------------------------------------------------

    private fun readBandInfo() {
        val eq = effects.equalizer
            ?: runCatching { Equalizer(0, 0) }.getOrNull().also { probe = it }

        if (eq != null) {
            runCatching {
                bandCount = eq.numberOfBands.toInt()
                val range = eq.bandLevelRange
                bandMin = range[0].toInt()
                bandMax = range[1].toInt()
                presetNames = (0 until eq.numberOfPresets.toInt())
                    .map { eq.getPresetName(it.toShort()).toString() }
                frequencies.clear()
                for (band in 0 until bandCount) frequencies.add(eq.getCenterFreq(band.toShort()))
            }
        }

        if (bandCount <= 0) {
            bandCount = 5
            bandMin = -1500
            bandMax = 1500
            presetNames = emptyList()
            frequencies.clear()
            frequencies.addAll(listOf(60000, 230000, 910000, 3600000, 14000000))
        }
    }

    private fun buildBands() {
        binding.bandContainer.removeAllViews()
        bandSeekBars.clear()
        bandLabels.clear()

        val accent = ColorStateList.valueOf(ThemeHelper.accentColor(this))
        val track = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.surface_3))
        val range = (bandMax - bandMin).coerceAtLeast(1)

        for (band in 0 until bandCount) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val freqLabel = TextView(this).apply {
                text = formatFrequency(frequencies.getOrElse(band) { 0 })
                layoutParams = LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT)
                setTextColor(ContextCompat.getColor(this@EqualizerActivity, R.color.text_secondary))
                textSize = 11f
            }

            val seek = SeekBar(this).apply {
                max = range
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
                progressTintList = accent
                thumbTintList = accent
                progressBackgroundTintList = track
            }

            val valueLabel = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT)
                gravity = Gravity.END
                setTextColor(ContextCompat.getColor(this@EqualizerActivity, R.color.text_primary))
                textSize = 11f
            }

            val bandIndex = band
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val level = bandMin + progress
                    effects.setBandLevel(bandIndex, level)
                    valueLabel.text = formatDecibel(level)
                    binding.presetDropdown.setText("", false)
                }

                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })

            row.addView(freqLabel)
            row.addView(seek)
            row.addView(valueLabel)
            binding.bandContainer.addView(row)

            bandSeekBars.add(seek)
            bandLabels.add(valueLabel)
        }
    }

    private fun refreshBandRows() {
        for (band in 0 until bandSeekBars.size) {
            val level = currentLevel(band)
            bandSeekBars[band].progress = (level - bandMin).coerceIn(0, bandSeekBars[band].max)
            bandLabels[band].text = formatDecibel(level)
        }
    }

    private fun currentLevel(band: Int): Int {
        val eq = effects.equalizer
        if (eq != null) {
            runCatching { return eq.getBandLevel(band.toShort()).toInt() }
        }
        return prefs.bandLevel(band)
    }

    // --- presets ------------------------------------------------------------

    private fun buildPresets() {
        if (presetNames.isEmpty()) {
            binding.presetLayout.visibility = View.GONE
            return
        }
        val adapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_1, presetNames
        )
        binding.presetDropdown.setAdapter(adapter)
        val current = prefs.eqPreset
        if (current in presetNames.indices) {
            binding.presetDropdown.setText(presetNames[current], false)
        }
        binding.presetDropdown.setOnItemClickListener { _, _, position, _ ->
            effects.selectPreset(position)
            refreshBandRows()
        }
    }

    // --- boost / bass / virtualizer ----------------------------------------

    private fun setupBoostSeekBars() {
        binding.bassSeek.max = 100
        binding.bassSeek.progress = prefs.bassBoost / 10
        updateBassLabel(binding.bassSeek.progress)
        binding.bassSeek.setOnSeekBarChangeListener(simpleListener { progress ->
            effects.setBassBoost(progress * 10)
            updateBassLabel(progress)
        })

        binding.virtSeek.max = 100
        binding.virtSeek.progress = prefs.virtualizer / 10
        updateVirtLabel(binding.virtSeek.progress)
        binding.virtSeek.setOnSeekBarChangeListener(simpleListener { progress ->
            effects.setVirtualizer(progress * 10)
            updateVirtLabel(progress)
        })

        binding.boostSeek.max = 100
        binding.boostSeek.progress = prefs.volumeBoost - 100
        updateBoostLabel(binding.boostSeek.progress)
        binding.boostSeek.setOnSeekBarChangeListener(simpleListener { progress ->
            effects.setVolumeBoost(100 + progress)
            updateBoostLabel(progress)
        })
    }

    private fun simpleListener(onChange: (Int) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) onChange(progress)
            }

            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        }

    private fun updateBassLabel(progress: Int) {
        binding.bassLabel.text = "${getString(R.string.bass_boost)}  ·  ${progress}%"
    }

    private fun updateVirtLabel(progress: Int) {
        binding.virtLabel.text = "${getString(R.string.virtualizer)}  ·  ${progress}%"
    }

    private fun updateBoostLabel(progress: Int) {
        binding.boostLabel.text =
            "${getString(R.string.volume_boost)}  ·  ${100 + progress}%"
    }

    private fun updateEnabledState() {
        val enabled = binding.eqSwitch.isChecked
        binding.bandContainer.alpha = if (enabled) 1f else 0.35f
        binding.presetLayout.alpha = if (enabled) 1f else 0.35f
        binding.resetButton.isEnabled = enabled
        for (seek in bandSeekBars) seek.isEnabled = enabled
        binding.eqDisabledHint.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    // --- formatting helpers -------------------------------------------------

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun formatFrequency(milliHertz: Int): String {
        val hz = milliHertz / 1000
        return if (hz >= 1000) {
            String.format(Locale.US, "%.1fk", hz / 1000.0)
        } else {
            "${hz}Hz"
        }
    }

    private fun formatDecibel(millibel: Int): String =
        String.format(Locale.US, "%+.1f", millibel / 100.0)
}
