# OwnTracks Auto

An Android and Android Auto companion app for an
[OwnTracks](https://owntracks.org/) recorder backend (e.g.
[owntracks-recorder](https://github.com/owntracks/recorder) or `owntrack-rs`).
It shows your tracked devices on a map, lets you browse each device's track for
a chosen day, and launches turn-by-turn navigation to a device, a track point,
or a dropped pin — on the phone and on the car's head unit.

## Features

- **Map overview** of all reporting devices, built on [MapLibre](https://maplibre.org/)
  with the keyless OpenFreeMap Liberty style.
- **Per-device daily tracks** — select a device to see its path for a day, step
  through days, and tap any point for its time and address.
- **Navigation** to a device, a track point, or a long-press "dropped pin",
  via Google Maps, Waze, or the system chooser.
- **Android Auto** support: a Points-of-Interest car app showing the device list
  and map on the head unit.
- **Auto-refresh** of today's data at a configurable interval while the app is
  on screen (it pauses when the app is backgrounded).
- **Optional HTTP Basic auth** for servers behind a reverse proxy.

## Requirements

- Android 6.0 (API 23) or newer.
- A reachable OwnTracks recorder HTTP API. The app calls the recorder's
  JSON endpoints (`/api/0/...`) under a configurable base URL.

## Setup

1. Install the app (see Building below) and open it.
2. Tap the **Settings** gear and set:
   - **Base URL** of your recorder, e.g. `http://192.168.1.254:8084`
     (the default). Both HTTP and HTTPS are supported.
   - **HTTP Basic credentials**, if your server requires them (leave the
     username blank to send no `Authorization` header).
   - **Navigation app** preference and **auto-refresh** interval.

Cleartext (plain HTTP) traffic is permitted for LAN use via
`app/src/main/res/xml/network_security_config.xml`. Narrow it to your server's
host if you want to forbid cleartext elsewhere.

## Building

The project uses the Gradle wrapper; no local Gradle install is needed.

```sh
# Debug build
./gradlew assembleDebug

# Release build (signed — see below)
./gradlew assembleRelease
```

Output APKs land in `app/build/outputs/apk/`.

### Signing release builds

`assembleRelease` always produces a **signed** APK — Android Auto rejects
debuggable sideloaded template apps, so the release build must be signed.

By default, if no release keystore is configured, the release build falls back
to the local **debug** key so the project builds out of the box. To sign with
your own release key:

1. Create a keystore once:

   ```sh
   keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 \
           -validity 10000 -alias owntracks
   ```

2. Copy the template and fill in your details:

   ```sh
   cp keystore.properties.sample keystore.properties
   # edit keystore.properties
   ```

   ```properties
   storeFile=release.jks          # absolute, or relative to the project root
   storePassword=...
   keyAlias=owntracks
   keyPassword=...
   ```

3. Build:

   ```sh
   ./gradlew assembleRelease
   ```

The build reads credentials from `keystore.properties` first, then from the
environment variables `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`,
`RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD` (useful for CI). `keystore.properties`
and all `*.jks` / `*.keystore` files are gitignored and must never be committed.

Verify a built APK's signature with:

```sh
"$ANDROID_HOME"/build-tools/*/apksigner verify --print-certs \
    app/build/outputs/apk/release/app-release.apk
```

## Testing

```sh
./gradlew test
```

## Project layout

```
app/src/main/java/com/owntracks/auto/
  car/        Android Auto car-app service, screens and session
  data/       Retrofit API service, DTOs, client factory
  domain/     Device model, repository, address + time helpers
  nav/        Navigation intent builder
  settings/   DataStore-backed settings
  ui/         Phone UI (Compose): map, device list, settings
```

## License

No license has been specified for this project.
