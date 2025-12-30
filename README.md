# WhatsApp Status Saver (Android)

A personal Android app for automatically backing up WhatsApp status media files.

## Features
- One-time persistent access to WhatsApp status folder via Storage Access Framework (SAF)
- Automatic background monitoring and backup of new status files
- 7-day retention period for backed-up media
- Grid view display of saved statuses
- Battery-optimized background service using WorkManager

## Technical Stack
- **Language:** Kotlin
- **Architecture:** MVVM with Room database
- **Background Processing:** WorkManager
- **Storage:** SAF (Storage Access Framework) for scoped storage compliance
- **Minimum SDK:** 26 (Android 8.0)
- **Target SDK:** 34 (Android 14)

## Migration Note
This project was migrated from Flutter to Kotlin for better native Android integration, particularly for:
- Persistent URI permissions with SAF
- Reliable background file monitoring
- Efficient scoped storage handling on Android 11+

Previous Flutter implementation archived in `old_flutter/` directory (not tracked in git).

## Setup
1. Clone the repository
2. Open in Android Studio
3. Build and run on Android device (Android 8.0+)

## Personal Project
This is a personal utility app not intended for Play Store distribution.
