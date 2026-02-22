## ⬇️ Download
[![Download](https://img.shields.io/badge/Download-Latest%20APK-brightgreen?style=for-the-badge&logo=github)](https://github.com/clement-mueller/Media-X-Manager/releases/latest)

# 🎵 Media X Manager

A sleek Android media controller app built with **Jetpack Compose** and **Material 3**. Control any media playing on your device — music, podcasts — from a single beautiful interface.

---

## ✨ Features

- 🖼️ **Album art display** — shows artwork from the active media session
- ⏯️ **Playback controls** — play/pause, skip forward, skip backward
- 🔄 **Auto-connects** to any active media session on the device
- 📡 **Live updates** — reacts instantly to track changes and playback state

---

## 📸 Screenshots

<img src="https://github.com/user-attachments/assets/73046284-7206-43f3-920c-97bf87b27ece" height="500">   
<img src="https://github.com/user-attachments/assets/acd05c6f-ca3a-42af-a76e-f71d76d3ac88" height="500"> 
<img src="https://github.com/user-attachments/assets/ac69dcd7-6a3a-4e5c-a65b-fddfe49182b2" height="500"> 
<img src="https://github.com/user-attachments/assets/bd0dfcbc-892a-4ce3-8b9a-3ea0cb851ba5" height="500">

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM (ViewModel + StateFlow) |
| Media | Android MediaSessionManager |
| Notifications | NotificationListenerService |
| Language | Kotlin |
| Min SDK | 26 (Android 8.0) |

---

### Required Permission

The app needs **Notification Listener** access to read media sessions. On first launch it will redirect you to:

> **Settings → Apps → Special app access → Notification access → Media X Manager** → Enable


---

## 📋 Permissions

| Permission | Reason |
|---|---|
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Required to access active media sessions |
| `MEDIA_CONTENT_CONTROL` | Required to send playback commands |
| `FOREGROUND_SERVICE` | Background service support |

---


## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.

---

<p align="center">Made with ❤️ using Jetpack Compose</p>

