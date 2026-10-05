# SMS-Bomb

Android app that periodically sends an SMS with a user-defined text to a
user-defined number at a configurable interval (seconds/minutes/hours).

> Use responsibly. Sending unsolicited bulk SMS may be illegal and will cost
> money depending on your mobile plan.

## Features

- Configurable recipient number, message text and interval
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
```

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
