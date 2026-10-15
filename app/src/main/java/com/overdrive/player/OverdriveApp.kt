package com.overdrive.player

import android.app.Application
import android.content.Context
import com.overdrive.player.audio.AudioEffectsManager
import com.overdrive.player.prefs.SettingsStore

/**
 * Application entry point. Holds process-wide singletons so that the playback
 * service and every activity share the same settings and audio effect state.
 */
class OverdriveApp : Application() {

    lateinit var prefs: SettingsStore
        private set

    lateinit var audioEffects: AudioEffectsManager
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = SettingsStore(this)
        audioEffects = AudioEffectsManager(this, prefs)
    }

    companion object {
        fun from(context: Context): OverdriveApp =
            context.applicationContext as OverdriveApp
    }
}
