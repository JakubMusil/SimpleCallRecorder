# SimpleCallRecorder

An Android app that automatically records phone calls and optionally uploads them to Google Drive. Sideload-only — not distributed via Google Play.

## Features

- **Automatic call recording** — detects incoming and outgoing calls via an Accessibility Service and TelephonyManager, starts/stops recording automatically
- **MP3 encoding** — audio is encoded on-device using LAME via AndroidLame
- **Local storage** — recordings saved to `Documents/MojeNahravky/` on external storage
- **Room database** — metadata (phone number, call type, date, duration, file size) persisted locally
- **Google Drive upload** — optional background upload via WorkManager with exponential back-off retry
- **Google Sign-In** — authenticate with a Google account directly in Settings
- **Recordings list** — browse, manually upload, or delete individual recordings from the main screen
- **Boot persistence** — Accessibility Service auto-restarts after device reboot if previously enabled

## Requirements

- Android 8.0 (API 26) or higher
- **Must be sideloaded** (installed via ADB or direct APK) — not available on Google Play
- On Android 13+ (API 33): enable **Install unknown apps** for the installer app and grant **Restricted Settings** for the accessibility service

## Setup Instructions

### 1. Grant Permissions

On first launch the app requests:
- `RECORD_AUDIO`
- `READ_PHONE_STATE`
- `POST_NOTIFICATIONS` (Android 13+)
- `WRITE_EXTERNAL_STORAGE` / `READ_EXTERNAL_STORAGE` (Android 8–9 only)

### 2. Enable the Accessibility Service

The app cannot record calls without the Accessibility Service. A dialog will prompt you automatically, or go manually:

**Settings → Accessibility → Downloaded apps → Call Recorder Service → Enable**

#### Android 13+ (Restricted Settings)

If the toggle is greyed out:
1. Go to **Settings → Apps → SimpleCallRecorder**
2. Tap the three-dot menu → **Allow restricted settings**
3. Return to Accessibility and enable the service

### 3. Configure Google Drive Upload (optional)

1. Open the app → tap the **Settings** icon (top-right)
2. Toggle **Upload to Google Drive** on
3. Tap **Sign In** and authenticate with your Google account
4. Subsequent recordings will be automatically queued for upload when connected to the internet

## Build Instructions

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK with API 34 platform tools

### Steps

```bash
git clone https://github.com/JakubMusil/SimpleCallRecorder.git
cd SimpleCallRecorder
./gradlew assembleDebug
```

The APK is output to `app/build/outputs/apk/debug/app-debug.apk`.

To install directly to a connected device:

```bash
./gradlew installDebug
```

## Project Structure

```
app/src/main/
├── AndroidManifest.xml
└── java/com/simplecallrecorder/
    ├── MainActivity.kt              # Recordings list + permission/accessibility checks
    ├── SettingsActivity.kt          # Google Sign-In + cloud upload toggle
    ├── BootReceiver.kt              # BOOT_COMPLETED broadcast receiver
    ├── data/
    │   ├── Recording.kt             # Room entity
    │   ├── RecordingDao.kt          # DAO queries
    │   └── RecordingDatabase.kt     # Singleton Room database
    ├── service/
    │   └── CallRecorderAccessibilityService.kt  # Call detection + recording lifecycle
    ├── audio/
    │   └── AudioRecordManager.kt    # AudioRecord → LAME MP3 encoder
    ├── drive/
    │   └── DriveServiceHelper.kt    # Google Drive REST API helper
    ├── worker/
    │   └── UploadWorker.kt          # WorkManager coroutine worker for uploads
    ├── ui/
    │   └── RecordingsAdapter.kt     # RecyclerView ListAdapter
    └── util/
        └── PreferencesManager.kt    # SharedPreferences wrapper
```

## Permissions Notes

| Permission | Purpose |
|---|---|
| `RECORD_AUDIO` | Capture microphone audio during calls |
| `READ_PHONE_STATE` | Detect call state changes |
| `READ_CALL_LOG` | Identify call direction (incoming/outgoing) |
| `FOREGROUND_SERVICE_MICROPHONE` | Required on API 34+ for microphone foreground services |
| `INTERNET` | Upload recordings to Google Drive |
| `RECEIVE_BOOT_COMPLETED` | Re-enable recording after reboot |