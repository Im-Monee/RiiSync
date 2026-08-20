<p align="center">
  <img src="app/src/main/res/drawable/banner.png" width="100%" />
</p>

<p align="center">
  <a href="https://opensource.org/licenses/MIT"><img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License: MIT"></a>
  <a href="https://developer.android.com/"><img src="https://img.shields.io/badge/Platform-Android-green.svg" alt="Platform: Android"></a>
  <a href="https://github.com/Im-Monee/RiiSync/releases"><img src="https://img.shields.io/github/v/release/Im-Monee/RiiSync?color=03A9F4" alt="GitHub Release"></a>
</p>

RiiSync is an Android app designed for people who modify Wii games and play them through Dolphin. It brings together GitHub, your mod folders, Riivolution patches and Dolphin's directories in one place, solving the problem of having to copy files over and over again and switch between browsers and apps.

I hope this won't be a pain anymore!

Use the integrated GitHub features, add your mods to Dolphin with ease, manage your patch files and enjoy the fact that they will always be synced.

---

## How it works? Let's check out the tabs.

**Git & Changes**: Sign in with a Personal Access Token, search public repositories and users, manage your stuff: pull, push, switch branches, all from the app.

**Modding**: You don't need a GitHub repo for this part. Pick any mod folder on your device, link it to Dolphin in one tap, be able to edit your XML patch files and RiiSync watches it for changes from then on.

**Settings**: Be able to make the app yours: change theme, download a fully database of Wii Icons and Covers to see them on your linked mods for better view, manage caches and more.

## What would be the trick?

**Git + Modding together**: Keep your mod inside a GitHub repo and the two features reinforce each other: pull new changes, and the file watcher picks them up and pushes them straight into Dolphin using Shizuku ultra permissions. No manual copying, ever.

---

## Detailed Features

### GitHub
- Search public repositories and browse user profiles.
- Manage your own repos with a Personal Access Token. (pull, push, branch switching)
- Work with repositories already stored locally on your device.

### Modding & Dolphin Management
- Link any local mod folder to Dolphin's internal directories (via Shizuku), no root needed.
- Live file watcher keeps your mod and Dolphin in sync automatically.
- Move a mod's source folder without breaking its Dolphin link.
- Works with both official Dolphin and MMJR2. (Medard22 VBI)

### Riivolution (part of the Modding Tab)
- Detects and parses Riivolution XML patches inside your mods.
- Built-in editor for tweaking patches directly.
- Validates patch files before they're used.

### Game icons & covers
- Downloads an artwork database (animated Wii channel icons + GameTDB covers) right from Settings: you can decide to fetch only for your current synced mods or locally download the entire database.
- Automatic fetch for the respective covers in all regions: EN, US, IT, DE, FR, ES, NL, PT, JA, KO and ZH: your game ID inside your XML patch file will be used to download a proper cover, later applied.

### Everything else
- GitHub tokens are stored in the Android Keystore, not in plain text.
- Detects connection loss and blocks actions that need network access before they fail halfway through.
- Guided onboarding walks you through permissions, preferences and future decisions on first launch.
- Material 3 UI with proper light/dark toggle theme.

---

## Installation

1. Grab the latest APK for your device's architecture from the [Releases](https://github.com/Im-Monee/RiiSync/releases) page (currently **v1.0.1**).
2. Install it, then make sure [Shizuku](https://shizuku.rikka.app/) is running — RiiSync needs it to link mods to Dolphin.
3. Open the app and follow the setup wizard; it'll check permissions and get everything ready.

---

## Credits

- **Developer:** Mone
- **Metadata & assets:** Nintendo, GameTDB
- **Built with:** JGit, Shizuku, Jetpack Compose, Coil, Jsoup

---

<p align="center">
  <i>An integrated modding workspace for the Wii community.</i>
  <br>
  <i><b>AI was used in the making of this software.</i></b>
</p>
