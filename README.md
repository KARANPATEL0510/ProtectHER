# ProtectHER

ProtectHER is a security-focused Android application built to help users recognize risky situations, monitor suspicious behavior, and quickly notify trusted contacts in emergencies.

The app combines real-time risk analysis, location awareness, smart follow detection, and emergency communication tools to support personal safety.

## Why this project exists

Safety apps often need to do more than send a simple alert. ProtectHER is built around the idea of proactive protection:

- continuously evaluating risk conditions
- detecting suspicious repeated patterns in nearby Bluetooth and Wi-Fi signals
- supporting emergency SMS and live location sharing
- enabling quick check-ins and safe-zone awareness

## Key features

- Live danger score based on location risk, time of day, movement, battery level, and network availability
- Smart Follow detection using Bluetooth and Wi-Fi signal recurrence to estimate suspicious following behavior
- SOS emergency alerts to trusted contacts
- Live location sharing for ongoing monitoring
- Safe-zone and geofence awareness for risky or restricted areas
- Safe check-in workflow for routine status updates
- Voice-triggered guardian mode for emergency interaction
- Contact management for trusted emergency contacts

## Tech stack

- Android (Java)
- AndroidX and Material Components
- Google Play Services Location
- Fused Location Provider and Geofencing APIs
- Bluetooth LE and Wi-Fi signal analysis
- SMS-based emergency communication

## Project structure

```text
ProtectHER/
├── app/
│   ├── src/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── release/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
├── gradle.properties
├── local.properties
├── README.md
├── .gitignore
├── gradle/
├── wrapper/
├── android/
└── app/release/
```

## Getting started

### Prerequisites

- Android Studio
- JDK 11 or newer
- Android SDK configured for API 29+
- A physical Android device or emulator

### Build and run

```bash
git clone https://github.com/KARANPATEL0510/ProtectHER.git
cd ProtectHER
./gradlew assembleDebug
```

Then open the project in Android Studio and run it on a connected device or emulator.

## Permissions used

The app may request permissions for:

- location access
- SMS sending
- Bluetooth scanning and connection
- Wi-Fi access
- microphone access
- notifications

These permissions support live safety monitoring, tracking, and emergency response actions.

## Important notes

- This app is intended for personal safety support and should be used responsibly.
- Emergency features depend on device permissions, connectivity, battery status, and trusted-contact setup.
- For public or production deployment, additional security review, compliance checks, and testing are recommended.

## License

This project does not currently include an explicit license file. If the project will be distributed publicly, add a license before release.

