# HealthConnect Export — Project Documentation

## Overview

**HealthConnect Export** — Android-приложение для экспорта данных Google Health Connect в JSON-формат с опциональной синхронизацией на Google Drive и отправкой на webhook.

### Core scenario

User selects health data types and a date range, then exports to JSON files (one per day). Files can be auto-synced to Google Drive and/or sent to a webhook URL. Scheduled daily/weekly exports via WorkManager.

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin 2.1.20 |
| UI | Jetpack Compose + Material3 |
| Build | Gradle KTS + AGP 9.1.1 |
| Health API | `androidx.health.connect:connect-client:1.1.0` |
| Google Drive | `google-api-services-drive:v3`, `google-http-client-gson` |
| Auth | `play-services-auth:21.0.0` (OAuth 2.0) |
| Background | WorkManager (`work-runtime-ktx:2.9.0`) |
| Serialization | `kotlinx-serialization-json:1.6.2` |
| Testing | JUnit 4.13.2 + Mockito 5.11.0 |
| Linting | ktlint 12.1.0 |
| Coverage | JaCoCo 0.8.11 (XML + HTML + CSV) |
| CI | GitHub Actions |
| minSdk / targetSdk / compileSdk | 28 / 36 / 36 |
| JVM | 21 |

---

## Architecture

```
MainActivity (ComponentActivity)
  └─ ExportScreen (Compose, LazyColumn)
       └─ ExportViewModel (AndroidViewModel)
            ├─ HealthConnectRepository — read Health Connect API
            ├─ LocalExportRepository  — save JSON to device
            ├─ GoogleDriveRepository  — sync with Google Drive
            └─ WebhookRepository      — POST to webhook URL
       └─ DailyExportWorker (WorkManager, background)
```

### Project structure

```
healthconnect-export/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/com/healthconnect/export/
│       │   ├── MainActivity.kt
│       │   ├── data/
│       │   │   └── DataModels.kt       # DailyHealthRecord, ExportConfig, enums, mappers
│       │   ├── repository/
│       │   │   ├── HealthConnectRepository.kt
│       │   │   ├── LocalExportRepository.kt
│       │   │   ├── GoogleDriveRepository.kt
│       │   │   └── WebhookRepository.kt
│       │   ├── util/
│       │   │   └── LocaleManager.kt
│       │   ├── viewmodel/
│       │   │   └── ExportViewModel.kt
│       │   ├── ui/
│       │   │   ├── ExportScreen.kt
│       │   │   └── theme/
│       │   │       └── AppTheme.kt
│       │   └── worker/
│       │       └── DailyExportWorker.kt
│       ├── main/res/
│       │   ├── values/strings.xml
│       │   ├── values/themes.xml
│       │   └── xml/health_connect_permissions.xml
│       └── test/java/com/healthconnect/export/
│           ├── data/
│           │   ├── HumanReadableMapperTest.kt
│           │   └── DataModelsSerializationTest.kt
│           ├── repository/
│           │   └── WebhookRepositoryTest.kt
│           └── worker/
│               └── DailyExportWorkerTest.kt
├── badges/                          # Coverage badge SVGs (auto-committed by CI)
├── build                            # Build script (bash)
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── AGENTS.md                        # This file
└── README.md
```

---

## Data flow

```
UI → ViewModel → HealthConnectRepository (read records)
                → LocalExportRepository (save JSON)
                → GoogleDriveRepository (sync, optional)
                → WebhookRepository (POST, optional)
```

### JSON file format

- File name: `health_YYYY-MM-DD.json`
- One file per day
- Location: app's external files directory (`HealthConnectExport/`)

### JSON payload example

```json
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
```

---

## Unit tests

**Test files (96 tests total):**

| File | Tests | Scope |
|---|---|---|
| `HumanReadableMapperTest.kt` | 33 | 8 mapper functions: bodyPositionToString (3), specimenSourceToString (3), mealTypeToString (3), sleepStageToString (3), measurementLocationToString (3), menstruationFlowToString (3), nutritionMealTypeToString (3), exerciseTypeToString (6) + null/unknown/edge cases |
| `WebhookRepositoryTest.kt` | 13 | `isValidWebhookUrl()` — URL validation (valid, invalid, null, edge cases) |
| `DailyExportWorkerTest.kt` | 14 | `doWork()` (10): success, already exported, empty, exceptions, default config / `schedule()` (4): daily, weekly, manual, cancel |
| `DataModelsSerializationTest.kt` | 36 | Roundtrip serialization: DailyHealthRecord (5), ExportConfig (4), ExportFrequency (4), HealthDataType (2), ExportSummary (3), helper functions (3), edge cases (4), SpeedData (5), SerialName verification |

**Run tests:**

```bash
./gradlew testDebugUnitTest
```

**Coverage:**

```bash
./gradlew jacocoTestReport
# Report: app/build/reports/jacoco/jacocoTestReport/html/index.html

# Coverage gate (verification against thresholds):
./gradlew jacocoTestCoverageVerification
```

---

## Coverage gate (JaCoCo)

### Global rules

| Counter | Minimum | Current |
|---|---|---|
| LINE | ≥ 30% | ~32% |
| BRANCH | ≥ 12% | ~13% |
| INSTRUCTION | ≥ 15% | ~22% |
| CLASS | ≥ 45% | ~59% |

### Per-package rules

| Package | Counter | Minimum | Current |
|---|---|---|---|
| `com.healthconnect.export.worker.*` | LINE | ≥ 90% | ~97% |
| `com.healthconnect.export.data.*` | LINE | ≥ 70% | ~75% |
| `com.healthconnect.export.viewmodel.*` | LINE | ≥ 60% | ~64% |
| `com.healthconnect.export.util.*` | LINE | ≥ 15% | ~16% |
| `com.healthconnect.export.repository.*` | LINE | ≥ 10% | ~13% |

If coverage drops below any threshold, `jacocoTestCoverageVerification` fails with a `BUILD FAILED` error listing the violated rules. In CI, this makes the `coverage` job fail (blocking gate).

### Coverage badge

On push to `main`, `cicirello/jacoco-badge-generator` reads `jacocoTestReport.csv` and commits updated coverage/branches badges to `badges/`. The badges are displayed in `README.md`.

---

## CI/CD (GitHub Actions)

Workflow: `.github/workflows/build-apk.yml`

### Triggers

- **Push** to `main`, `develop`
- **Pull Request** to `main`
- **Push tag** `v*` (e.g., `v1.1`)
- **Manual** via `workflow_dispatch`

### Pipeline

```
Push
 ├─ lint       — lintRelease + ktlintCheck (continue-on-error)
 ├─ test       — testDebugUnitTest → upload test-results artifact
 ├─ coverage   — jacocoTestReport + jacocoTestCoverageVerification
 │                → on push to main: generate + commit coverage badge
 │                → upload coverage-html (7d) + coverage-xml (30d)
 └─ build-debug — assembleDebug → upload debug APK (7d)

Tag push (after lint + test + build-debug):
 └─ build-release
     ├─ bump versionName from tag, increment versionCode
     ├─ commit + push version bump to main
     ├─ decode keystore from KEYSTORE_BASE64 secret
     ├─ assembleRelease (signed)
     └─ upload release APK (30d)

Tag push (after build-release):
 └─ release
     ├─ download release APK
     ├─ generate changelog from git log
     └─ create GitHub Release with APK
```

### Build steps (detailed)

1. **Checkout** + JDK 21 + Android SDK
2. **Cache** Gradle (~/.gradle/caches, ~/.gradle/wrapper)
3. **Lint** (`lintRelease`)
4. **Ktlint** (`ktlintCheck`) — non-blocking (`continue-on-error: true`)
5. **Unit tests** (`testDebugUnitTest`) → upload `test-results` (7 days)
6. **JaCoCo coverage** (`jacocoTestReport`) + **verification** (`jacocoTestCoverageVerification`) in one Gradle invocation
7. **Generate coverage badge** (push to main only) — `cicirello/jacoco-badge-generator`
8. **Upload coverage** HTML (7 days) + XML (30 days)
9. **Debug APK** (`assembleDebug`) → upload (7 days)
10. **Version bump** (tag push only): `versionName` ← tag, `versionCode` ← +1
11. **Commit** version bump to `main`
12. **Decode keystore** from `KEYSTORE_BASE64` secret
13. **Release APK** (`assembleRelease`) with signing
14. **Rename APKs**: `HealthConnectExport-{version}.apk` and `-unsigned.apk`
15. **Upload** release APK (30 days)
16. **GitHub Release** (tag push only): changelog from git history + APK attachment

### GitHub Secrets

| Secret | Description |
|---|---|
| `KEYSTORE_BASE64` | `healthconnect-release.jks` in base64 |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias (default: `healthconnect`) |
| `KEY_PASSWORD` | Key password (falls back to `KEYSTORE_PASSWORD`) |

### Setup secrets in GitHub

1. Encode keystore to base64:
   ```bash
   base64 -w0 healthconnect-release.jks > healthconnect-release.jks.base64
   ```
2. Copy the entire content of `healthconnect-release.jks.base64`
3. Go to GitHub → Settings → Secrets and variables → Actions
4. Add secrets:
   - `KEYSTORE_BASE64` → paste the base64 string
   - `KEYSTORE_PASSWORD` → your keystore password
5. Delete `healthconnect-release.jks.base64` — it contains the full keystore

> **Security:** Never commit `.jks`, `.base64`, or password files to git. The `.gitignore` already excludes `*.jks`, `*.base64`, `.keystore_password.txt`.

### Release process

```bash
git tag v1.1
git push origin v1.1
# CI: bump version → build → create GitHub Release
```

### Artifacts

| Artifact | Retention | When |
|---|---|---|
| `test-results` | 7 days | Always |
| `coverage-html` | 7 days | Always |
| `coverage-xml` | 30 days | Always |
| `HealthConnectExport-debug` | 7 days | Always |
| `HealthConnectExport-release` | 30 days | Tag push only |

---

## Build and run

```bash
# Debug build only
./build

# Build, install, launch
./build --run

# Pull exported JSON files from device
./build --pull

# Direct gradle commands
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Permissions

| Permission | Purpose |
|---|---|
| `INTERNET` | Drive sync + webhook |
| `ACCESS_NETWORK_STATE` | Network check |
| `FOREGROUND_SERVICE` | Scheduled work |
| `RECEIVE_BOOT_COMPLETED` | Reschedule after reboot |
| `health.READ_*` | Health Connect data types (20 types) |

---

## Code conventions

- **Language**: Kotlin, official style (`code.style=official`)
- **UI**: Jetpack Compose + Material3
- **Architecture**: MVVM with manual DI (no DI framework)
- **Serialization**: `kotlinx.serialization` with `@Serializable`, `@SerialName`
- **Background**: WorkManager `CoroutineWorker`
- **Network**: `HttpURLConnection` in `Dispatchers.IO`
- **File format**: `health_YYYY-MM-DD.json`
- **UI language**: English
- **No separate Application class** — uses AndroidX Startup

---

## Scheduling

- **Manual**: Export on button press
- **Daily**: 24h interval via WorkManager `PeriodicWorkRequest`
- **Weekly**: 168h interval
- Default: **Daily** (set at app startup)
- Policy: `ExistingPeriodicWorkPolicy.KEEP`

---

## Google Drive setup

### 1. Google Cloud Console

1. Создайте **Android OAuth Client ID**:
   - Тип: **Android**
   - Package name: `com.healthconnect.export`
   - SHA-1: Release `2A:BF:A4:CA:62:59:78:A2:5D:78:FD:74:2D:CB:CA:07:D2:37:42:72`
2. Создайте **Web Application Client ID**:
   - Тип: **Web application**
   - Authorized redirect URIs: не нужны (используется для `requestIdToken`)
3. Настройте **OAuth consent screen** (External / Testing)
4. Включите **Google Drive API**
5. Включите **Identity Toolkit API**

### 2. Код

```kotlin
// GoogleDriveRepository.kt
fun getSignInOptions(): GoogleSignInOptions {
    return GoogleSignInOptions.Builder()
        .requestEmail()
        .requestIdToken("730530422387-dveo97h089iesh4etmj74q9dn8j221f1.apps.googleusercontent.com")
        .requestScopes(Scope(DriveScopes.DRIVE_FILE))
        .build()
}
```

**Важно:**
- `requestIdToken()` использует **Web Client ID** — это обязательно для Google Sign-In
- Android Client ID с SHA-1 остаётся в Google Cloud Console для верификации приложения
- Scope `DRIVE_FILE` даёт доступ к файлам, созданным этим приложением

---

## Webhook

- URL: configurable in UI
- Auth: optional Bearer token (stored in `ExportConfig`)
- Method: POST
- Content-Type: `application/json`
- Timeout: 15 seconds
- URL validation: client-side (red highlight on invalid URL)
- Works with both manual and scheduled exports
