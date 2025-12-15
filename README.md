# 📱 Status Saver

<p align="center">
  <img src="ICON.png" alt="Status Saver Logo" width="120" height="120">
</p>

<p align="center">
  <b>A beautiful, lightweight WhatsApp status saver built with Flutter</b>
</p>

<p align="center">
  <a href="../../actions/workflows/build.yml">
    <img src="../../actions/workflows/build.yml/badge.svg" alt="Build Status">
  </a>
  <img src="https://img.shields.io/badge/Flutter-3.24-02569B?logo=flutter" alt="Flutter">
  <img src="https://img.shields.io/badge/Dart-3.x-0175C2?logo=dart" alt="Dart">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android" alt="Platform">
  <img src="https://img.shields.io/badge/License-MIT-green" alt="License">
</p>

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| 📷 **Live Status** | View WhatsApp statuses directly in the app |
| 💾 **Save All** | Save all images or videos with one tap |
| 🗄️ **Smart Cache** | Automatic 7-day caching with expiry tracking |
| 📂 **Organized** | Separate tabs for images and videos |
| 🎨 **Modern UI** | Glassmorphism design with smooth animations |
| 🔐 **Permissions** | Full Android 10-14+ compatibility |

---

## 📸 Screenshots

<p align="center">
  <i>Coming soon...</i>
</p>

---

## 🚀 Download

### Option 1: GitHub Releases
Download the latest APK from the [Releases](../../releases) page.

### Option 2: Build Artifacts
1. Go to the [Actions](../../actions) tab
2. Click on the latest successful workflow run
3. Download from **Artifacts** section:
   - `release-apk` - Production ready
   - `debug-apk` - For testing

---

## 🛠️ Build Locally

```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/status-saver.git
cd status-saver

# Install dependencies
flutter pub get

# Generate app icons
flutter pub run flutter_launcher_icons

# Run the app
flutter run

# Build release APK
flutter build apk --release
```

---

## 📱 App Screens

| Screen | Description |
|--------|-------------|
| **Live Status** | View current WhatsApp statuses with save options |
| **Saved** | Access permanently saved statuses |
| **Cache** | View auto-cached statuses with expiry info |
| **Viewer** | Fullscreen image/video viewer with save & share |

---

## 🏗️ Architecture

```
lib/
├── config/          # Theme & constants
├── models/          # Data models with Hive
├── services/        # Business logic
├── providers/       # State management
├── screens/         # App screens
└── widgets/         # Reusable UI components
```

**Tech Stack:**
- **State Management:** Provider
- **Local Storage:** Hive
- **Permissions:** permission_handler
- **UI:** Material Design 3 with custom glassmorphism

---

## 📋 Requirements

- Android 5.0 (API 21) or higher
- WhatsApp installed on device
- Storage permissions granted

---

## ⚠️ Important Notes

- This app requires storage permissions to access WhatsApp status folder
- On Android 11+, you may need to grant "All Files Access" permission
- iOS version functions as a saved status manager only (due to sandboxing)

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the project
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  Made with ❤️ using Flutter
</p>
