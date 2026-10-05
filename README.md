# SMS-Bomb

Android app that periodically sends an SMS with a user-defined text to a
user-defined number at a configurable interval (minutes or hours, minimum 10 minutes).

> Use responsibly. Sending unsolicited bulk SMS may be illegal and will cost
> money depending on your mobile plan.

## Use case: O2 "Unlimited On Demand" top-ups

The O2 *Mobile Unlimited On Demand* tariff includes 10 GB of high-speed data
per day. Once 80 % of that is used, O2 sends an SMS from **80112**; replying
with **WEITER** to **80112** books another 2 GB of high-speed data — free of
charge and repeatable without limit.

Doing this manually every time is tedious (and naive auto-reply loops forever
on O2's confirmation SMS), so this app simply sends `WEITER` to `80112` on a
fixed schedule — **every 30 minutes** is a good default. The input fields are
pre-filled accordingly; change them if your workflow differs.

## Features

- Configurable recipient number, message text and interval (minimum 10 minutes as abuse guard)
- Foreground service keeps sending while the app is in the background
- Long texts are automatically split into multipart SMS
- In-app send log and live status (sent count, countdown to next send)
- Inputs are persisted across app restarts

## Build

Requires JDK 17+ and an Android SDK (platform 34, build-tools 34.0.0).
Point `ANDROID_HOME` at the SDK or create `local.properties` with `sdk.dir=...`.

```bash
./gradlew assembleDebug        # builds app-debug.apk
./gradlew testDebugUnitTest    # runs JVM unit tests
bash release.sh -u             # bump version + clean build + tests
```

Releasing to F-Droid is documented in [dev/FDROID.md](dev/FDROID.md).

## Permissions

- `SEND_SMS` – requested at runtime
- `POST_NOTIFICATIONS` – requested at runtime on Android 13+
- `FOREGROUND_SERVICE_SPECIAL_USE` – the scheduler runs as a foreground service

## Architecture

| Class | Role |
|---|---|
| `SmsSchedulerEngine` | pure scheduling logic (unit tested) |
| `SendConfig` | validated immutable config (unit tested) |
| `IntervalParser` | amount+unit parsing/formatting (unit tested) |
| `SendLog` | bounded in-memory send log (unit tested) |
| `SmsSender` / `AndroidSmsSender` | SMS abstraction / platform impl |
| `SmsSchedulerService` | foreground service driving the engine |
| `MainActivity` | configuration UI + status/log view |
