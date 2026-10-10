<div align="center">

# 🛡️ ProtectHER

**Your personal safety companion for real-time risk awareness, smart detection, and emergency support**

[![Android](https://img.shields.io/badge/Android-Platform-3DDC84?style=for-the-badge&logo=android)](https://developer.android.com)
[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk)](https://www.java.com)
[![Material](https://img.shields.io/badge/Material-Design-2196F3?style=for-the-badge&logo=materialdesign)](https://m3.material.io)
[![Location API](https://img.shields.io/badge/Google-Location-4285F4?style=for-the-badge&logo=google)](https://developers.google.com/maps/documentation/android-sdk)

</div>

ProtectHER is a safety-first Android app designed to help users stay alert, monitor suspicious activity, and notify trusted contacts quickly during emergencies. It combines real-time risk scoring, smart follow detection, location sharing, and emergency communication in one mobile experience.

## ✨ Why ProtectHER?

Everyday safety is not just about reacting after something goes wrong — it is also about recognizing risk early.

ProtectHER helps users by:

- tracking risky conditions in real time
- identifying suspicious repeated Bluetooth and Wi-Fi patterns
- measuring danger using multiple live signals
- sending SOS alerts to trusted contacts
- supporting safe check-ins and location updates

## 🚀 Core Features

| Feature | What it does |
|---|---|
| Live Danger Score | Evaluates risk using location, time, movement, battery, and network conditions |
| Smart Follow Detection | Detects recurring nearby signals to estimate suspicious following behavior |
| SOS Alert System | Sends emergency SMS messages to trusted contacts |
| Live Location Sharing | Lets users share location updates with support contacts |
| Safe Zones | Tracks risky areas and geofence-based awareness |
| Voice Guardian Mode | Supports quick, voice-driven emergency interactions |
| Trusted Contacts | Makes emergency communication faster and more reliable |

## 🧠 How It Works

1. The app monitors live sensor and location data.
2. It calculates a danger score based on situational context.
3. It scans nearby Bluetooth and Wi-Fi signals for pattern repetition.
4. If the risk crosses a threshold, the app can trigger alerts and updates.
5. Trusted contacts receive emergency notifications and location details when needed.

## 🛠️ Tech Stack

- Android (Java)
- AndroidX + Material Design
- Google Play Services Location
- Bluetooth LE scanning
- Wi-Fi signal monitoring
- SMS emergency alerts
- Geofencing and live tracking APIs

## 📁 Project Structure

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
├── README.md
├── .gitignore
├── gradle/
├── wrapper/
├── android/
└── app/release/
```

## 🏁 Getting Started

### Prerequisites

- Android Studio
- JDK 11 or newer
- Android SDK configured for API 29+
- Android device or emulator

### Run the app

```bash
git clone https://github.com/KARANPATEL0510/ProtectHER.git
cd ProtectHER
./gradlew assembleDebug
```

Then open the project in Android Studio and run it on a connected device or emulator.

## 🔐 Permissions Used

The app may request access to:

- location
- SMS
- Bluetooth scanning and connection
- Wi-Fi access
- microphone
- notifications

These permissions help support emergency alerts, live tracking, and real-time safety monitoring.

## ⚠️ Notes

- This app is intended for personal safety support and should be used responsibly.
- Emergency features depend on device permissions, internet access, battery state, and trusted contact configuration.
- For public deployment, it is recommended to perform additional testing, compliance checks, and security review.

## 📜 License

This project currently does not include an explicit license file. If you plan to distribute it publicly, add a license before release.

<div align="center">

Made with ❤️ for personal safety and awareness.

</div>

