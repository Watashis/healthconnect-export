# HealthConnect Export 📱

[![CI](https://github.com/kas-cor/healthconnect-export/actions/workflows/build-apk.yml/badge.svg)](https://github.com/kas-cor/healthconnect-export/actions/workflows/build-apk.yml)
[![Coverage](https://raw.githubusercontent.com/kas-cor/healthconnect-export/main/badges/coverage.svg)](https://github.com/kas-cor/healthconnect-export/actions/workflows/build-apk.yml)
[![Branches](https://raw.githubusercontent.com/kas-cor/healthconnect-export/main/badges/branches.svg)](https://github.com/kas-cor/healthconnect-export/actions/workflows/build-apk.yml)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![Kotlin](https://img.shields.io/badge/kotlin-2.3.21-purple)](https://kotlinlang.org)

> Coverage badge auto-updates on every push to `main` via CI.

Android app for exporting Google Health Connect data to JSON format with optional Google Drive sync and webhook delivery.

## Features ✨

- **Daily export** — one day = one JSON file (`health_YYYY-MM-DD.json`)
- **Local storage** — files saved on device
- **Scheduled export** — daily or weekly via WorkManager
- **Google Drive sync** — optional, auto-sync to Drive
- **Webhook delivery** — POST JSON to a URL (with optional Bearer auth)
- **Every-2-hours webhook** — send current day's data to webhook every 2 hours (no Drive sync)
- **20 data types** — steps, heart rate, sleep, calories, exercise, nutrition, and more
- **Date range selection** — last 7/30 days or custom range
- **Manual + automatic** — export on demand or on schedule

## Architecture 🏗️

```
MainActivity → ExportScreen (Compose) → ExportViewModel
  ├─ HealthConnectRepository  — read Health Connect API
  ├─ LocalExportRepository    — save JSON files
  ├─ GoogleDriveRepository    — sync to Drive
  ├─ WebhookRepository        — POST to webhook
  ├─ DailyExportWorker        — scheduled background export (daily/weekly)
  └─ Every2HoursWebhookWorker — periodic webhook-only export (every 2 hours)
```

## Quick start 🚀

```bash
# Debug build
./build

# Build, install, launch
./build --run

# Pull exported JSON files
./build --pull

# Direct gradle
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Testing 🧪

```bash
# Run all unit tests
./gradlew testDebugUnitTest

# Coverage report + gate check
./gradlew jacocoTestReport jacocoTestCoverageVerification
# Open: app/build/reports/jacoco/jacocoTestReport/html/index.html
```

**Test suites (262 total):**

| File | Tests | Scope |
|---|---|---|
| `WebhookRepositoryTest` | 39 | sendRecords via local HTTP server (success/error/auth/special chars/JSON body) + URL validation |
| `DataModelsSerializationTest` | 33 | Roundtrip serialization: DailyHealthRecord, ExportConfig, enums, SpeedData |
| `HighlightJsonSyntaxTest` | 31 | JSON syntax highlighting: strings, numbers (int/float/sci), booleans, null, nested objects, arrays, escaped quotes |
| `HumanReadableMapperTest` | 27 | 8 mapper functions: bodyPosition, specimenSource, sleepStage, exerciseType, etc. |
| `DailyExportWorkerTest` | 25 | `doWork()` (success/already-exported/empty/exceptions) + `schedule()` (daily/weekly/manual/cancel) |
| `LocalExportRepositoryTest` | 24 | File operations: save, list, cleanup, isExported, filename format |
| `GoogleDriveRepositoryTest` | 23 | Drive sync: upload, list, download, delete, scopes, special characters |
| `Every2HoursWebhookWorkerTest` | 18 | doWork (happy path, blank URL, exceptions) + schedule/cancel + constants |
| `ExportDataUseCaseTest` | 15 | Export steps: permissions, reading, saving, webhook, Drive sync, health check |
| `ExportViewModelTest` | 13 | ViewModel states: loading, export, error, permissions, schedule |
| `LocaleManagerTest` | 12 | localeDisplayName all branches, saveLocale/getSavedLocale |

## CI/CD 🚀

Workflow: `.github/workflows/build-apk.yml`

### Triggers

- **Push** to `main`, `develop`
- **Pull Request** to `main`
- **Push tag** `v*` (e.g., `v1.1`)
- **Manual** via `workflow_dispatch`

### Pipeline

```
Push → Lint + Ktlint → Unit tests
     → Coverage (JaCoCo report + gate with 9 rules + badge)
     → Debug APK
     └─ Tag push → Decode keystore → Sign → Release APK → GitHub Release
```

### Coverage gate

After every push, JaCoCo verifies coverage against 9 rules. If any rule fails, the `coverage` job fails:

| Scope | Rule | Threshold |
|---|---|---|
| **Project-wide** | LINE | ≥ 30% |
| | BRANCH | ≥ 12% |
| | INSTRUCTION | ≥ 15% |
| | CLASS | ≥ 45% |
| **worker** package | LINE | ≥ 95% |
| **data** package | LINE | ≥ 70% |
| **viewmodel** package | LINE | ≥ 65% |
| **util** package | LINE | ≥ 50% |
| **repository** package | LINE | ≥ 35% |

On push to `main`, a coverage badge is auto-committed to `badges/`.

### Artifacts

| Artifact | Retention | Content |
|---|---|---|
| `test-results` | 7 days | HTML test reports |
| `coverage-html` | 7 days | JaCoCo HTML report |
| `coverage-xml` | 30 days | JaCoCo XML for Codecov/SonarCloud |
| `HealthConnectExport-debug` | 7 days | Debug APK |
| `HealthConnectExport-release` | 30 days | Signed release APK (tag push only) |

### GitHub Secrets

| Secret | Description |
|---|---|
| `KEYSTORE_BASE64` | `healthconnect-release.jks` in base64 |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias (default: `healthconnect`) |
| `KEY_PASSWORD` | Key password (falls back to `KEYSTORE_PASSWORD`) |

### Release

```bash
git tag v1.1
git push origin v1.1
# CI: bump version → build → create GitHub Release
```

[Workflow](.github/workflows/build-apk.yml)

## Google Drive setup 🔐

Для Google Sign-In требуется **два** OAuth Client ID в одном Google Cloud Project:

| Тип | Client ID | Назначение |
|---|---|---|
| **Android** (OAuth) | `730530422387-oaffqtrvfd1rqr6jn1uq8791mgbbpmlj` | Проверка SHA-1 + package name (Google Play Services) |
| **Web application** (OAuth) | `730530422387-dveo97h089iesh4etmj74q9dn8j221f1` | `requestIdToken()` в коде |

### SHA-1 fingerprints

| Build type | SHA-1 fingerprint |
|---|---|
| **Release** | `2A:BF:A4:CA:62:59:78:A2:5D:78:FD:74:2D:CB:CA:07:D2:37:42:72` |
| **Debug** | `8B:BB:D1:45:E2:61:5B:02:57:E1:3F:26:29:1A:AF:F0:2C:40:77:73` |

### OAuth consent screen

1. Откройте [OAuth consent screen](https://console.cloud.google.com/apis/credentials/consent)
2. Тип: **External** → **Testing** (не требуется верификация)
3. Добавьте **Test users** — свой email
4. На шаге **Scopes** добавьте `https://www.googleapis.com/auth/drive.file`

### Required APIs

- [Google Drive API](https://console.cloud.google.com/apis/library/drive.googleapis.com) — `ENABLED`
- [Identity Toolkit API](https://console.cloud.google.com/apis/library/identitytoolkit.googleapis.com) — `ENABLED`

### Verify signing

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
# SHA-1: 2abfa4ca625978a25d78fd742dcbca07d2374272
```

## Webhook 📡

After export, health data can be sent to any URL as a POST request.

### Configuration

- **URL** — configurable in UI, validated client-side (red highlight on invalid)
- **Auth** — optional Bearer token (stored in `ExportConfig`)
- **Auto-send** — toggle in UI to send automatically after each export
- Works with both manual and scheduled exports

### Request format

| Attribute | Value |
|---|---|
| **Method** | `POST` |
| **Content-Type** | `application/json` |
| **Authorization** | `Bearer <token>` (optional) |
| **Timeout** | 15 seconds |

### Payload structure

The JSON body is wrapped in a `messages` envelope:

```json
{
  "messages": [
    {
      "date": "2026-05-23",
      "steps": { "total_steps": 12453, "records_count": 480 },
      "heart_rate": { "avg_bpm": 72.5, "min_bpm": 55, "max_bpm": 142, "records_count": 18 },
      "sleep": {
        "total_duration_minutes": 420,
        "sleep_stages": { "Deep sleep": 90, "Light sleep": 195, "REM sleep": 105, "Awake": 30 },
        "records_count": 1
      },
      "metadata": {
        "app_version": "1.0.0",
        "export_timestamp": "2026-05-23T23:00:00",
        "timezone": "Europe/Moscow",
        "source_device": "test_device"
      }
    }
    // ... more days
  ]
}
```

Each element in the `messages` array is a `DailyHealthRecord` — one per exported day — containing all selected health data types. The full list of supported fields includes steps, heart rate, sleep, calories, distance, floors climbed, active calories, weight, body fat, blood pressure, blood glucose, oxygen saturation, body temperature, respiratory rate, hydration, resting heart rate, exercises, nutrition, speed, and menstruation.

## Scheduling ⏰

- **Manual**, **Daily** (24h), or **Weekly** (168h)
- **Every 2 hours** — optional webhook-only sending of current day's data (checkbox in Schedule section)
- Default: Daily (auto-enabled at startup)
- Uses WorkManager `PeriodicWorkRequest`

## Permissions

| Permission | Purpose |
|---|---|
| `INTERNET` | Drive sync + webhook |
| `ACCESS_NETWORK_STATE` | Network check |
| `FOREGROUND_SERVICE` | Scheduled work |
| `RECEIVE_BOOT_COMPLETED` | Reschedule after reboot |
| `health.READ_*` | Health Connect data types (20 types) |

## Tech stack

| Component | Version |
|---|---|
| Language | Kotlin 2.3.21 |
| UI | Jetpack Compose + Material3 (BOM 2026.05) |
| Build | AGP 9.1.1 / Gradle 9.5.1 |
| Health Connect | `connect-client:1.1.0` |
| Google Drive | `google-api-services-drive:v3-rev20240123`, `google-http-client-gson:2.1.0` |
| Auth | `play-services-auth:21.6.0` |
| Background | WorkManager `work-runtime-ktx:2.11.2` |
| Serialization | `kotlinx-serialization-json:1.11.0` |
| minSdk / targetSdk / compileSdk | 28 / 36 / 36 |
| JVM | 21 |
| Testing | JUnit 4.13.2, Mockito 5.23.0, mockito-kotlin 6.3.0 |
| Coverage | JaCoCo 0.8.11 |
| CI | GitHub Actions |

## Changelog 📋

See [CHANGELOG.md](CHANGELOG.md) for full release history.

| Version | Date | Highlights |
|---|---|---|
| [v1.5](https://github.com/kas-cor/healthconnect-export/releases/tag/v1.5) | 2026-06-08 | Every-2-hours webhook worker, test webhook button, file sorting descending, dependency updates (Kotlin 2.3.21, Gradle 9.5.1) |
| [v1.4](https://github.com/kas-cor/healthconnect-export/releases/tag/v1.4) | 2026-06-07 | Date range fix: endDate includes today, 7/30 day presets corrected |
| [v1.3](https://github.com/kas-cor/healthconnect-export/releases/tag/v1.3) | 2026-05-27 | Webhook payload format `{"messages": [...]}`, export progress with per-day bars, cancel button, day-by-day read |
| [v1.2](https://github.com/kas-cor/healthconnect-export/releases/tag/v1.2) | 2026-05-27 | **+99 tests** (295 total), repo coverage ~55%, WebhookRepository rewritten with local HTTP server |
| [v1.1](https://github.com/kas-cor/healthconnect-export/releases/tag/v1.1) | 2026-05-26 | Coverage gate, ktlint, API upgrade, webhook auth, +83 tests, russian L10n |
| [v1.0](https://github.com/kas-cor/healthconnect-export/releases/tag/v1.0) | — | Initial release: JSON export, Drive sync, webhook, WorkManager, Material3 UI |

## License 📄

MIT
