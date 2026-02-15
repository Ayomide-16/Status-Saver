# 📱 Status Saver

[![Build Android APK](https://github.com/Ayomide-16/Status-Saver/actions/workflows/build.yml/badge.svg)](https://github.com/Ayomide-16/Status-Saver/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
![Android](https://img.shields.io/badge/Android-8.0%2B-brightgreen)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue)

A native Android app for viewing, caching, and saving WhatsApp status media (images & videos). It uses the **Storage Access Framework (SAF)** for scoped-storage compliance and runs a background service to automatically cache new statuses — no root required.

---

## ✨ Features

- **Live Status Browsing** — View all currently active WhatsApp statuses in a grid, separated into Images and Videos tabs
- **One-Tap Save** — Save any status permanently to your device with a single tap
- **Auto-Caching** — Background service monitors the WhatsApp status folder and caches new media automatically, even when the app is closed
- **Auto-Save Mode** — Optional toggle to automatically save _all_ new statuses as they appear
- **Configurable Retention** — Cached statuses are kept for a configurable number of days (default 7), then cleaned up automatically
- **Full-Screen Viewer** — Pinch-to-zoom on images (PhotoView) and gesture-based video playback controls
- **Multi-Select** — Long-press to enter selection mode; batch save, share, or delete multiple statuses at once
- **Dark / Light Theme** — Toggle between a WhatsApp-green light theme and a dark theme; preference is persisted
- **Boot Persistence** — The caching service restarts automatically after device reboots
- **Offline & Private** — No internet permission required; all data stays on your device

---

## 🏗️ Architecture

The project follows **MVVM** (Model-View-ViewModel) with a repository layer:

```
UI (Activities / Fragments)
        │
        ▼
  StatusViewModel          ← LiveData, loading state, user actions
        │
        ▼
  StatusRepository         ← data operations, SAF file I/O, MediaStore
        │
   ┌────┴────┐
   ▼         ▼
Room DB    SAF / FileSystem
```

| Layer | Key Classes |
|-------|-------------|
| **UI** | `MainActivity`, `StatusSectionFragment`, `StatusListFragment`, `FullScreenViewActivity`, `StatusAdapter` |
| **ViewModel** | `StatusViewModel` |
| **Repository** | `StatusRepository` |
| **Database** | `AppDatabase`, `StatusDao`, `StatusEntity`, `DownloadedStatus` |
| **Background** | `StatusMonitorService` (foreground service), `StatusBackupWorker` (WorkManager) |
| **Utilities** | `SAFHelper`, `PermissionHelper`, `ThemeManager`, `Constants` |

---

## 🛠️ Tech Stack

| Category | Technology |
|----------|-----------|
| Language | Kotlin 1.9 |
| Min / Target SDK | 26 (Android 8.0) / 34 (Android 14) |
| UI | Material Design 3, ViewBinding, ViewPager2, RecyclerView + SelectionTracker |
| Image Loading | [Coil](https://coil-kt.github.io/coil/) 2.5 (image + video frames) |
| Database | Room 2.6 with KSP annotation processing |
| Background Work | WorkManager 2.9, Foreground Service |
| Storage | Storage Access Framework (SAF), MediaStore API, DocumentFile |
| Zoom | [PhotoView](https://github.com/Chrisbanes/PhotoView) 2.3 |
| Concurrency | Kotlin Coroutines 1.7 |
| CI/CD | GitHub Actions — builds debug APK on every push / PR |

---

## 📋 Permissions

| Permission | Purpose |
|-----------|---------|
| `READ_EXTERNAL_STORAGE` | Read media (Android ≤ 12) |
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` | Read media (Android 13+) |
| `MANAGE_EXTERNAL_STORAGE` | All-files access fallback |
| `FOREGROUND_SERVICE` | Background status monitoring |
| `POST_NOTIFICATIONS` | Show service notification |
| `RECEIVE_BOOT_COMPLETED` | Restart service after reboot |

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** (latest stable recommended)
- **JDK 17**
- An Android device or emulator running **Android 8.0+**

### Build & Run

```bash
# Clone the repository
git clone https://github.com/Ayomide-16/Status-Saver.git
cd Status-Saver

# Build the debug APK
./gradlew assembleDebug

# The APK will be at:
# app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in Android Studio, sync Gradle, and click **Run**.

### First Launch

1. Grant the requested storage permissions
2. When prompted, navigate to **Android → media → com.whatsapp → WhatsApp → Media → .Statuses** and grant folder access
   > **Note:** For WhatsApp Business the path uses `com.whatsapp.w4b` instead. The exact path may vary by device or WhatsApp version.
3. The app remembers this folder — you only need to do this once

---

## 📂 Project Structure

```
app/src/main/
├── java/com/statussaver/app/
│   ├── MainActivity.kt              # Main screen, navigation, permission flow
│   ├── StatusSaverApp.kt            # Application class, WorkManager init
│   ├── data/
│   │   ├── database/                # Room database, entities, DAO
│   │   └── repository/             # StatusRepository (data layer)
│   ├── service/
│   │   └── StatusMonitorService.kt  # Foreground service for live monitoring
│   ├── worker/
│   │   └── StatusBackupWorker.kt    # WorkManager periodic backup task
│   ├── ui/
│   │   ├── fragments/               # StatusSectionFragment, StatusListFragment
│   │   ├── selection/               # Multi-select helpers (SelectionTracker)
│   │   ├── StatusAdapter.kt         # RecyclerView grid adapter
│   │   ├── FullScreenViewActivity.kt
│   │   ├── FullScreenMediaAdapter.kt
│   │   └── VideoGestureHandler.kt
│   ├── util/                        # Constants, SAFHelper, PermissionHelper, ThemeManager
│   └── viewmodel/
│       └── StatusViewModel.kt
└── res/
    ├── layout/                      # 12 XML layouts
    ├── drawable/                     # 30+ vector icons & shapes
    ├── menu/                        # Toolbar, bottom nav, full-screen menus
    ├── values/                      # Colors, strings, themes (green + dark)
    └── xml/                         # FileProvider paths
```

---

## 🔒 Privacy

Status Saver does **not** collect, transmit, or share any personal data. It requires no internet permission. All status files, cached media, and preferences are stored locally on your device. Cached files are automatically deleted after the configured retention period.

---

## 🤝 Contributing

Contributions are welcome! To get started:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes (`git commit -m 'Add my feature'`)
4. Push to your branch (`git push origin feature/my-feature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
