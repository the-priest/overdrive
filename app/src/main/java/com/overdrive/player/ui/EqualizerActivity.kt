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
import com.google.android.material.chip.Chip
import com.overdrive.player.OverdriveApp
import com.overdrive.player.R
import com.overdrive.player.audio.RockPresets
import com.overdrive.player.databinding.ActivityEqualizerBinding
import com.overdrive.player.util.ThemeHelper
import com.overdrive.player.util.applySystemBarPadding
import com.overdrive.player.util.enableDarkEdgeToEdge
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

    private var eqAvailable = true
    private var bandCount = 0
    private var bandMin = -1500
    private var bandMax = 1500
    private var presetNames: List<String> = emptyList()
    private val frequencies = ArrayList<Int>()

    private val bandSeekBars = ArrayList<SeekBar>()
    private val bandLabels = ArrayList<TextView>()
    private var updatingChips = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.apply(this)
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()
        binding = ActivityEqualizerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarPadding(top = true, bottom = true)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.eqSwitch.isChecked = prefs.equalizerEnabled

        readBandInfo()
        buildBands()
        buildRockPresets()
        buildDevicePresets()
        setupBoostSeekBars()

        binding.eqSwitch.setOnCheckedChangeListener { _, checked ->
            effects.setEqualizerEnabled(checked)
            updateEnabledState()
        }
        binding.resetButton.setOnClickListener {
            effects.resetEqualizer(bandCount)
            refreshBandRows()
            clearPresetSelection()
        }

        refreshBandRows()
        updateEnabledState()
    }

    // --- equalizer bands ----------------------------------------------------

    private fun readBandInfo() {
        val attached = effects.equalizer
        // Nothing playing yet: briefly open an equalizer just to learn the
        // device's band layout, then let it go again.
        val probe = if (attached == null) {
            runCatching { Equalizer(0, 0) }.getOrNull()
        } else {
            null
        }
        val eq = attached ?: probe

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
        probe?.let { runCatching { it.release() } }

        if (bandCount <= 0) {
            eqAvailable = eq != null
            bandCount = 5
            bandMin = -1500
            bandMax = 1500
            presetNames = emptyList()
            frequencies.clear()
            frequencies.addAll(listOf(60000, 230000, 910000, 3600000, 14000000))
        }
        binding.eqUnavailable.visibility = if (eqAvailable) View.GONE else View.VISIBLE
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
                minimumHeight = dp(44)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val freqLabel = TextView(this).apply {
                text = formatFrequency(frequencies.getOrElse(band) { 0 })
                layoutParams = LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT)
                setTextColor(ContextCompat.getColor(this@EqualizerActivity, R.color.text_secondary))
                textSize = 12f
                typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
            }

            val seek = SeekBar(this).apply {
                max = range
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
                progressTintList = accent
                thumbTintList = accent
                progressBackgroundTintList = track
                contentDescription = freqLabel.text
            }

            val valueLabel = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT)
                gravity = Gravity.END
                setTextColor(ContextCompat.getColor(this@EqualizerActivity, R.color.text_primary))
                textSize = 12f
            }

            val bandIndex = band
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val level = bandMin + progress
                    effects.setBandLevel(bandIndex, level)
                    valueLabel.text = formatDecibel(level)
                    clearPresetSelection()
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

    private fun buildRockPresets() {
        val names = mapOf(
            RockPresets.FLAT to R.string.preset_flat,
            RockPresets.METAL to R.string.preset_metal,
            RockPresets.ROCK to R.string.preset_rock,
            RockPresets.DJENT to R.string.preset_djent,
            RockPresets.DOOM to R.string.preset_doom,
            RockPresets.VOCAL to R.string.preset_vocal
        )
        val accent = ThemeHelper.accentColor(this)
        val surface = ContextCompat.getColor(this, R.color.surface_2)
        val chipBg = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accent, surface)
        )
        RockPresets.ALL.forEach { preset ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                setText(names.getValue(preset))
                isCheckable = true
                isCheckedIconVisible = false
                chipBackgroundColor = chipBg
                chipStrokeWidth = 0f
                tag = preset
                setOnCheckedChangeListener { button, checked ->
                    if (!checked || updatingChips) return@setOnCheckedChangeListener
                    applyRockPreset(button.tag as RockPresets.Preset)
                }
            }
            binding.rockPresets.addView(chip)
        }
    }

    private fun applyRockPreset(preset: RockPresets.Preset) {
        val levels = RockPresets.levelsFor(preset, frequencies, bandMin, bandMax)
        effects.setCustomCurve(levels)
        if (!binding.eqSwitch.isChecked) binding.eqSwitch.isChecked = true
        refreshBandRows()
        if (presetNames.isNotEmpty()) binding.presetDropdown.setText("", false)
    }

    private fun buildDevicePresets() {
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
            clearChips()
        }
    }

    private fun clearChips() {
        updatingChips = true
        binding.rockPresets.clearCheck()
        updatingChips = false
    }

    private fun clearPresetSelection() {
        clearChips()
        if (presetNames.isNotEmpty()) binding.presetDropdown.setText("", false)
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
        binding.bassLabel.text = getString(R.string.effect_label, getString(R.string.bass_boost), progress)
    }

    private fun updateVirtLabel(progress: Int) {
        binding.virtLabel.text = getString(R.string.effect_label, getString(R.string.virtualizer), progress)
    }

    private fun updateBoostLabel(progress: Int) {
        binding.boostLabel.text =
            getString(R.string.effect_label, getString(R.string.volume_boost), 100 + progress)
    }

    private fun updateEnabledState() {
        val enabled = binding.eqSwitch.isChecked && eqAvailable
        binding.bandContainer.alpha = if (enabled) 1f else 0.35f
        binding.presetLayout.alpha = if (enabled) 1f else 0.35f
        binding.resetButton.isEnabled = enabled
        for (seek in bandSeekBars) seek.isEnabled = enabled
        binding.eqDisabledHint.visibility =
            if (binding.eqSwitch.isChecked || !eqAvailable) View.GONE else View.VISIBLE
        binding.eqSwitch.isEnabled = eqAvailable
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
        String.format(Locale.US, "%+.1f dB", millibel / 100.0)
}
