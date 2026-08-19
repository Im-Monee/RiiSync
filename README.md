<p align="center">
  <img src="app/src/main/res/drawable/banner.png" width="100%" />
</p>

# RiiSync

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform: Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![GitHub Release](https://img.shields.io/github/v/release/Im-Monee/RiiSync?color=03A9F4)](https://github.com/Im-Monee/RiiSync/releases)

RiiSync is a comprehensive Android utility designed for the Wii modding community. It facilitates seamless synchronization between local Git-based mod repositories and the Dolphin emulator environment, providing an integrated workflow for mod management, asset acquisition, and system integration.

---

## Key Features

### Git & Development Integration
* **Synchronized Repositories**: Clone, pull, and manage Riivolution mod repositories directly within the application.
* **Local Workspace Management**: Integrated file explorer and change tracker for local mod files.
* **Commit & Push Support**: Complete Git workflow including staging changes, committing with descriptions, and pushing to remotes.
* **Branch Management**: Fluid switching between development branches for complex modding projects.

### Modding & Emulator Integration
* **Privileged Linking**: Utilizes the Shizuku API to perform privileged filesystem operations, linking local mod folders directly to Dolphin's internal directories.
* **Riivolution Support**: Advanced parsing of Riivolution XML patches with real-time integrity verification.
* **Dolphin Environment Support**: Full compatibility with both Official Dolphin and MMJR2 (Medard22 VBI) builds.
* **Smart Relocation**: Dynamically re-map mod source folders while automatically updating emulator links.

### Asset & Database Management
* **High-Speed Asset Sync**: Integrated Git-based synchronization for high-quality animated Wii channel icons.
* **Automated Cover Fetching**: Real-time acquisition of vertical box art from GameTDB with multi-region support (EN, US, IT, DE, FR, ES, NL, PT, JA, KO, ZH).
* **Targeted Asset Discovery**: Bandwidth-efficient mode that downloads only the icons and covers required for your synchronized mods.
* **Live UI Refresh**: Instant updates of mod visuals without requiring application restarts or tab switching.

### System & Security
* **Hardware-Backed Security**: Encrypted storage of GitHub Personal Access Tokens using the Android Keystore system.
* **Connection Intelligence**: Resilient network monitoring with automated state recovery and connection-aware action gating.
* **Onboarding Experience**: Multi-stage interactive setup process to ensure environment health and proper permission configuration.
* **Material 3 Design**: Professional, adaptive user interface with full optimization for both Light and Dark modes.

---

## Installation

1. Navigate to the [**Releases**](https://github.com/Im-Monee/RiiSync/releases) page.
2. Download and install the latest `v1.0.0` APK matching your device architecture.
3. Ensure the [**Shizuku**](https://shizuku.rikka.app/) service is active on your device.
4. Follow the interactive setup guide upon initial launch.

---

## Credits

* **Developer**: Mone
* **Metadata & Assets**: Nintendo, GameTDB
* **Core Libraries**: JGit, Shizuku, Jetpack Compose, Coil, Jsoup.

---
<p align="center">
  <i>Providing an integrated modding experience for the Wii community.</i>
</p>
