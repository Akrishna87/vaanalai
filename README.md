# 📻 Vaanoli (வானொலி): radio for Android

Vaanoli plays live internet radio from around the world, including your own
country's stations. The stations come from the free, community-run
[Radio Browser](https://www.radio-browser.info) directory, which lists about
50,000 of them. There are no ads, no account and no tracking.

**Download:** the latest build is always the **Vaanoli.apk** file on the
[`radio-latest` release](https://github.com/Akrishna87/Akrishna87/releases/tag/radio-latest).
Open it on your phone and allow installing from your browser when asked.
Each new build installs as an update over the last one.

## What it does

- **Home** shows the most popular stations in your country, worked out from
  your SIM card or phone settings. Tap the country chip to change it, or pick
  🌍 Worldwide. Below that are popular stations worldwide and your recently
  played stations.
- **Browse** by genre or language: Tamil, Hindi, Bollywood, Malayalam,
  Telugu, Carnatic, devotional, news, talk, pop, rock, jazz, classical, lo-fi
  and more. Each one can show stations in your country or worldwide.
- **Search** by station name, city, language or genre, either everywhere or
  only in your country.
- **Favourites**: tap ♡ on any station to keep it. You can also **add a
  station by its stream link**, including `.pls` and `.m3u` playlist links,
  if the station isn't in the directory. The ⋮ menu reorders favourites or
  removes them.
- **Plays in the background**, with controls in the notification, on the
  lock screen, and from Bluetooth headphones and the car. **Previous / Next**
  move to the previous or next station in the list you picked from.
- **Shows the song that's on**, when the station sends song titles.
- **Reconnects by itself** when the connection drops or the station's server
  hangs up. After a long pause it jumps back to live instead of playing
  old audio.
- **Sleep timer** (moon icon on the full player): stops the radio after 15
  minutes to 2 hours, fading out gently.
- **Android Auto**: browse Favourites, Recently played and Popular stations
  from the car's screen, or say "play <station> on Vaanoli".
- A headphone or car **play** button brings back the last station even after
  the app was closed.
- Dark and light themes follow the phone's setting.

## A few honest notes

- Streaming uses mobile data: roughly 30–60 MB an hour for most stations
  (64–128 kbps).
- Some stations in the directory are off the air at any given moment. If one
  won't play, the app says so; just pick another.
- Many stations only broadcast over plain `http`, so the app allows
  unencrypted connections for the audio streams. The station directory is
  always fetched over `https`. The app has no access to your files,
  contacts or location, and only asks for internet access.
- Favourites and history live only on this phone and are left out of cloud
  backups.

## Building

This is a standard Gradle project (Kotlin, Jetpack Compose, Media3/ExoPlayer),
built with JDK 17:

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

CI (`.github/workflows/radio-apk.yml`) builds the release APK on every push
that touches `radio-android/`, signs it, runs it on an Android 14 emulator
(`ci/smoke-test.sh`), and publishes it to the `radio-latest` release with the
emulator's screenshots.

**Signing:** release builds are signed with Isaialai's private key
(`music-player-android/signing/release.keystore.gpg`, unlocked in CI with the
`SIGNING_PASSPHRASE` secret), so no public key is ever used. See
[`music-player-android/SECURITY.md`](../music-player-android/SECURITY.md).
