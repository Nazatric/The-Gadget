# The Gadget

A native Android music player and local HTML-game shelf. The interface is Jetpack Compose. Playback is Media3. Library indexing, artwork, and settings stay off the UI thread. A small C++ probe reads codec, rate, depth, channels, and ReplayGain tags from a file descriptor.

The home screen is native Compose: a near-black field, a central sphere, orbital glass nodes, and a status pill that uses the username stored on this device. It is not a default Material screen.

## Sections

Home, username, features, account, config, games, and music. The status pill uses the username stored on the device. The default is `listener`.

Music reads the real library through MediaStore, with an optional folder picked through the Storage Access Framework. It plays through a Media3 session, so notification, lock screen, headset, and Bluetooth controls are the system session. Queue, shuffle, repeat, speed, seek, favorites, playlists, history, search, and sort are implemented. Gapless transitions are on unless turned off. ReplayGain is a real PCM gain stage, not a volume boost. The now-playing screen says when the output path cannot be bit-perfect.

Games ships empty. Add a zip or folder that contains `index.html`. The game opens in its own process and WebView. The rest of the app stays Compose.

## Build

```
./gradlew test assembleRelease
```

Release is signed with the debug keystore unless `GADGET_STORE_FILE`, `GADGET_STORE_PASSWORD`, `GADGET_KEY_ALIAS`, and `GADGET_KEY_PASSWORD` are set. Do not commit a keystore.

## Attribution

- Instrument Serif and Lato, SIL Open Font License. See `third_party/fonts`.
- Playback uses AndroidX Media3 (Apache-2.0).
- Local HTML games are not included.
