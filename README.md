# Overdrive — Android Music Player

A no-nonsense, hard-rock themed music player. No ads, no accounts, no
subscriptions. It scans your phone, plays your library, and gives you a proper
equalizer with a volume booster that goes up to 200%.

## Features

- **Library scan** — reads your device's music through `MediaStore` and sorts it
  into Songs, Albums, Artists and Favorites tabs.
- **Real playback** — Media3 / ExoPlayer under the hood, so you get gapless
  auto-advance to the next track, a proper queue, shuffle and repeat
  (off / all / one).
- **Background playback** — a foreground media service with a lock-screen /
  notification transport. Keeps playing with the screen off.
- **Equalizer** — graphic EQ with all of the device presets plus per-band
  sliders, bass boost and a 3D virtualizer.
- **Volume boost up to 200%** — mapped to a `LoudnessEnhancer`, i.e. up to
  +20 dB of gain (about 4x perceived loudness at the top). Applies only to
  Overdrive's own audio session, never to other apps.
- **Favorites** — long-press any track to add/remove it.
- **Rock styling** — dark charcoal + blood-red (Guns N' Roses) palette, bold
  condensed type, lightning-bolt icon. Pick a Blood Red / Amped Amber / Steel
  Blue accent in Settings.
- **Sensible settings** — pause when headphones unplug, start in shuffle, hide
  short clips, rescan the library.

## Install

A signed, ready-to-install APK is at the project root:

```
Overdrive.apk
```

Copy it to your phone and open it (enable "Install unknown apps" for your file
manager/browser if prompted). Or over USB:

```bash
adb install -r Overdrive.apk
```

The app asks for audio access on first launch so it can find your music.

## Build from source

Requirements: JDK 17, Android SDK with platform `android-37.2` and build-tools
`37.0.0` (the Gradle wrapper downloads Gradle 9.6 automatically).

```bash
./gradlew :app:assembleDebug     # debug APK
./gradlew :app:assembleRelease   # signed release APK
./gradlew installDebug           # build + install on a connected device
```

APK output:
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

> The demo release signing key lives at `overdrive-release.jks` (store/key
> password `overdrive`). Replace it with your own before publishing anywhere.

## Project layout

```
app/src/main/java/com/overdrive/player/
├── OverdriveApp.kt          # process-wide singletons (settings, audio effects)
├── audio/                   # Equalizer / BassBoost / Virtualizer / LoudnessEnhancer
├── data/                    # MediaStore scan + Song/Album/Artist models
├── player/                  # Media3 service, controller connection, queue helpers
├── prefs/                   # typed SharedPreferences wrapper
├── ui/                      # activities, fragments, adapters, ViewModel
└── util/                    # artwork loader, formatters, theme helper
```

## Notes on the volume boost

The boost uses Android's `LoudnessEnhancer` on the player's audio session, so it
is applied digitally. Crank it past ~150% and poorly recorded tracks or tiny
speakers may clip. That is exactly how the "volume booster" apps on the store do
it — there is no magic beyond the platform effect.
