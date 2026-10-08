# CarNavigationHelper

Android app for a phone that lives in the car. On start it plays music, works out whether you are at home or at work, and after a short countdown starts Waze (or iGO) to the other place. A floating button brings the app back from the navigation app.

## How it works

1. The app starts (for example automatically when the phone connects to the car).
2. The music player starts a shuffled playlist from your music folder.
   - Pressing **Next** (in the app, the media notification or the car's buttons) blacklists the song that was playing: it disappears from the track list, is never played again and the next song starts (also when the music was paused). The thumb-down button on a row of the track list blacklists any song the same way, but keeps the music playing or paused as it was. A song that ends by itself, **Previous** and Next after the last song has ended don't blacklist anything.
   - With **Volume normalisation** on, all songs play at the same loudness (target −18 LUFS, EBU R128). Each song gets one fixed gain, never more than its peak allows, so there is no clipping and no compression. The loudness is measured once per song in the background and stored; a song not measured yet starts at an estimate and glides to its exact level once measured.
   - Under the song title the screen shows codec and bitrate, e.g. `FLAC · 905 kbps`.
3. The app gets the current location. If no fresh GPS fix arrives within 20 seconds, it uses the last known location, which is fine for a parked car.
4. Near **home** it picks **work** as the destination, near **work** it picks **home** (within about 1 km, `LOCATION_RADIUS = 0.01`°). Anywhere else it waits for you to choose.
5. A countdown runs (15 seconds by default). You can tap another button, open the navigation app without a destination, or close the app to stop it.
6. **Waze** is started with the destination only once the internet actually works:
   - A good connection answers one quick check within 2 seconds, and Waze starts right away.
   - On weak mobile reception the screen shows *Waiting for stable internet…* and the app waits, with no time limit, until 3 checks in a row succeed (2 seconds apart, 5 seconds each). Then Waze starts.
   - The check is a tiny request to `https://connectivitycheck.gstatic.com/generate_204`. Android's own "connected" status isn't used because it stays on while a weak mobile connection can't carry data, and Waze started then can't calculate the route.
   - **iGO** works offline and starts immediately.
7. The floating car button stays on top of the navigation app. Tapping it brings the app back.

Back on the main screen works like **Close app**: it cancels the countdown or a waiting start, closes the app and leaves the floating button.

The app, its launch screen (logo) and the system bars follow the phone's light/dark mode automatically. A switch while the app is open doesn't restart the screen or the countdown. On Android 13+ the launcher icon also supports themed icons.

## Settings

**Settings** (gear icon on the main screen):
- Navigation app: Waze or iGO
- Countdown in seconds
- Home and work latitude/longitude (only valid values are saved: latitude −90…90, longitude −180…180)

**Player settings** (gear icon on the player screen):
- Music folder, picked with the system folder picker
- **Update content** rescans the folder (audio files in all subfolders). Opens the folder picker if no folder was picked yet. A rescan clears the blacklist; measured loudness is kept. A rescan is fast for songs whose file size did not change, since their tags are kept.
- **Volume normalisation** on/off (on by default). Switching applies to the playing song at once.
- **Blacklist (N)** opens its own screen with the blacklisted songs. The close button next to a song takes it off the list; it is added to the running playlist without restarting the music.

After installing an update that changes the music database, tap **Update content** once. Home/work coordinates and other settings are kept.

## Permissions

| Permission | Why |
|---|---|
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | Floating button over the navigation app. It also allows the app to start Waze from the background after waiting for internet. |
| Precise location (`ACCESS_FINE_LOCATION`) | Detecting home/work. If it was denied for good, *Grant permission* opens the app's system settings page. |
| Internet, network state | Internet check before starting Waze |
| Foreground service (media playback, special use) | Music playback and the floating button service |

Music files are read only through the folder you picked; no "All files access" is needed.

## Project structure

```
app/src/main/java/com/smsoft/carnavigationhelper/
  ui/AppNavigation.kt          MainActivity and the navigation graph (Main / Player / Settings / PlayerSettings)
  ui/screen/main/              Main screen: location, countdown, internet check, starting Waze/iGO
  ui/screen/player/            Player screen and its ViewModel (MediaController)
  ui/screen/settings/          Navigation settings
  ui/screen/player_settings/   Music folder, rescan, volume normalisation
  ui/screen/blacklist/         Blacklisted songs with remove buttons
  ui/floating/                 Floating button UI
  service/ButtonService.kt     Service that shows/hides the floating button
  service/AudioPlaybackService.kt  Media3 MediaSessionService
  repository/                  DataStore preferences
  data/database/               Room playlist (songs found by the scan, blacklist flag, measured loudness)
  module/                      Hilt modules
app/src/main/java/com/un4seen/bass/  BASS audio library bindings, BassPlayer (Media3 player on top of BASS, plays FLAC/APE/MP3 and more) and LoudnessAnalyzer (EBU R128 loudness measurement)
app/src/main/jniLibs/        BASS native libraries
library/                     Floating window library (vendored compose-floating-window)
```

## Tech stack

Kotlin 2.4, Jetpack Compose (Material 3), core-splashscreen, Navigation Compose with type-safe routes, Hilt, Room, DataStore, Media3 session, Google Play services location, BASS. Gradle 9.8, Android Gradle Plugin 9.4. minSdk 26, targetSdk/compileSdk 37.

## Building

Needs JDK 21 (Android Studio's bundled JBR works) and the Android SDK with platform 37.

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Timing constants for the internet check and location are at the bottom of [MainViewModel.kt](app/src/main/java/com/smsoft/carnavigationhelper/ui/screen/main/MainViewModel.kt).

## Third-party

- [BASS](https://www.un4seen.com/) audio library by Un4seen Developments. Free for non-commercial use; commercial use needs a licence.
- [compose-floating-window](https://github.com/only52607/compose-floating-window), vendored in `library/`.
