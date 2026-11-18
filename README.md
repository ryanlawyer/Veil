# Veil

**A calm, private home screen for Android TV.** Veil replaces your TV's home screen with a clean, cinematic
one: big artwork, what you're watching, your apps, and nothing else. No ads, no "recommended for you",
no tracking.

![Home](screenshots/01-home.png)

| | |
|---|---|
| ![Colours from the artwork](screenshots/02-home-colours-from-artwork.png) | ![Title logos](screenshots/03-home-title-logo.png) |
| ![Privacy panel](screenshots/04-privacy-panel-nextdns.png) | ![Veil menu](screenshots/05-veil-menu.png) |
| ![Synced lyrics](screenshots/07-music-synced-lyrics.png) | ![Word-by-word lyrics](screenshots/08-music-word-by-word-lyrics.png) |
| ![Visualizer](screenshots/10-visualizer-1.png) | ![Visualizer settings](screenshots/09-visualizer-settings.png) |

More in [`screenshots/`](screenshots).

> **Installing:** Veil is built from source with *your own* API keys. The easiest way is to give this repo
> to an AI coding agent (for example Claude Code) and ask it to follow
> **[`AGENT_INSTALL.md`](AGENT_INSTALL.md)**. It checks your TV, asks for your keys, backs up your settings,
> and confirms before it changes anything.

## What you get

- **Home**: built around **Netflix, Prime Video and Stremio** as big branded tiles (pin and reorder any app;
  *All apps* is always at the end). Move onto a service and the screen fills with what's popular there:
  full-screen artwork with its title logo and a slow pan and zoom, a new title every 15 seconds. The accent
  colour follows the artwork. Sign in to Trakt and a **Continue watching** row appears above the tiles
  (opens the episode in Nuvio). Clock and weather at the top.
- **Privacy pill**: one switch for system-wide ad and tracker blocking via encrypted DNS (NextDNS if you
  have it, otherwise AdGuard DNS), with "pause for 15 minutes / 1 hour" for sites that break. With NextDNS
  it also shows how many trackers it blocked.
- **Veil Ambient** (screensaver): a slow slideshow of artwork. While music plays (Spotify etc.) it shows
  the album art with **synced lyrics** (word by word when available), or a full-screen **music visualizer**
  that picks out the kick, snare, hi-hats and vocals, locks onto the tempo and pulses on the beat.
- **Themes**, colours taken from the artwork, glassy cards that catch the light, soft UI sounds, seasonal
  touches (snow in December) and a wake-up intro when the TV turns on.
- **Made for weak TV hardware**: everything heavy is pre-rendered, and it was profiled on a real device.

## Requirements

- An Android TV or Google TV, **Android 9 or newer**, controllable over ADB (developer options).
- A computer (Windows, macOS or Linux) with JDK 17 and the Android SDK command-line tools. Gradle comes
  with the repo (`source/gradlew`).
- **Your own free API keys** (the repo contains none, apart from Veil's shared Trakt app):

| Key | Needed for | Where |
|---|---|---|
| TMDB API key | Artwork and title logos (**required**) | themoviedb.org → Settings → API |
| Trakt client ID | Not needed: Veil's shared Trakt app is built in. Optional, to use your own app | trakt.tv/oauth/applications → New app |
| NextDNS profile + API key | NextDNS blocking and the blocked counter (optional) | my.nextdns.io |

Keys go in `source/keys.properties` (copy `keys.properties.example`), which git ignores. Trakt works
out of the box through Veil's shared Trakt app (`source/trakt.properties`, public on purpose: it has no
secret). Your Trakt *account* is never put into the app: you sign in on the TV with a code, like any TV
app. To use your own Trakt app instead, pick **Use your own Trakt app** on the Connect Trakt screen and
enter its Client ID with the remote, or set `TRAKT_CLIENT_ID` in `keys.properties`.

## Permissions, and why

A TV can't ask for these with a pop-up, so they're granted over ADB (the install guide explains each one first).

| Permission | What it's used for | What it does **not** do |
|---|---|---|
| Home screen + Home-button helper (accessibility service) | Makes the remote's Home button open Veil | Sees only the Home key and which app is in front. It can't read the screen or other keys |
| Notification access | Reads "now playing" (song, artist, cover) from media apps | Ignores the contents of your notifications |
| Audio (RECORD_AUDIO) | The visualizer reads the TV's **output** loudness live | No microphone. Nothing is recorded, stored or sent |
| Secure settings (WRITE_SECURE_SETTINGS) | Turns the Private DNS blocking on and off; keeps the Home helper enabled | Nothing else |
| Internet | The services below | - |

## Privacy

- **No accounts, analytics, ads or Veil servers.** Veil talks directly to these services over HTTPS:
  - **TMDB**: titles of what you're watching, to get artwork; and what's popular on Netflix and Prime Video
    in your region, plus the weekly trending list, for the service tiles.
  - **Trakt**: reads your watch progress with your own login and, only when you ask, marks items watched
    or removes them from Continue Watching (clears paused progress, hides the show from progress).
  - **Open-Meteo**: your chosen city, for the weather.
  - **LRCLIB**: the song currently playing (title, artist, album, length), to get lyrics.
  - **NextDNS API**: your blocked-tracker stats, if set up.
- **Your Trakt login** stays on the TV, in Veil's private storage (excluded from Android backups).
- **DNS blocking is off until you turn it on.** It uses Android's built-in Private DNS, so it covers every
  app on the TV, and it can be paused or switched off at any time.

## Undo

The install guide has the agent save your TV's current settings and write an **undo script** for your TV
first. Running it brings back your original home screen, screensaver, DNS and permissions. Veil itself can
then be removed with `adb uninstall dev.veil.launcher`. Nothing on the TV's system is removed or modified.

## Building

```sh
cd source
cp keys.properties.example keys.properties   # then fill in your keys
./gradlew assembleRelease                    # → app/build/outputs/apk/release/app-release.apk
```
See [`AGENT_INSTALL.md`](AGENT_INSTALL.md) for installing, permissions and performance notes.

## Credits

- Built with [Claude Code](https://claude.com/claude-code). Kotlin + Jetpack Compose for TV.
- Artwork and logos from [TMDB](https://www.themoviedb.org). *This product uses the TMDB API but is not
  endorsed or certified by TMDB.*
- Watch progress from [Trakt](https://trakt.tv). *Veil uses Trakt but is not endorsed or certified by Trakt.*
- Lyrics from [LRCLIB](https://lrclib.net), weather from [Open-Meteo](https://open-meteo.com) (CC BY 4.0).
- [Manrope](https://github.com/sharanda/manrope) font, under the SIL Open Font License 1.1
  ([`licenses/OFL-Manrope.txt`](licenses/OFL-Manrope.txt)).
- NextDNS, Trakt, TMDB, Spotify and Nuvio are trademarks of their owners. Veil isn't affiliated with any of them.

## License

[GPL-3.0](LICENSE). You're free to use, change and share Veil. If you share a modified version, it has to
be open source under the same license.
