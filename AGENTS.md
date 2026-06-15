# HealthConnect Export — Project Documentation

## Overview

**HealthConnect Export** — Android-приложение для экспорта данных Google Health Connect в JSON-формат с опциональной синхронизацией на Google Drive и отправкой на webhook. Полная локализация: английский и русский языки.

### Core scenario

User selects health data types and a date range, then exports to JSON files (one per day). Files can be auto-synced to Google Drive and/or sent to a webhook URL. Scheduled daily/weekly exports via WorkManager. All UI strings use `stringResource()` and support EN/RU locale switching.

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin 2.3.21 |
| UI | Jetpack Compose + Material3 (BOM 2026.05) |
| Build | Gradle KTS + AGP 9.1.1 / Gradle 9.5.1 |
| Health API | `androidx.health.connect:connect-client:1.1.0` |
| Google Drive | `google-api-services-drive:v3-rev20240123`, `google-http-client-gson:2.1.0` |
| Auth | `play-services-auth:21.6.0` (OAuth 2.0) |
| Background | WorkManager (`work-runtime-ktx:2.11.2`) |
| Serialization | `kotlinx-serialization-json:1.11.0` |
| Testing | JUnit 4.13.2 + Mockito 5.23.0 + mockito-kotlin 6.3.0 |
| Linting | ktlint 14.2.0 |
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
            ├─ ExportDataUseCase          — export workflow (Flow<ExportStep>)
            ├─ DriveManager               — Google Drive sign-in/sync/sign-out
            ├─ WebhookManager             — webhook URL/token/send/test
            ├─ ScheduleManager            — schedule/cancel periodic exports
            ├─ HealthConnectRepository    — read Health Connect API (batch via TypeHandler)
            ├─ LocalExportRepository      — save JSON to device
            ├─ GoogleDriveRepository      — Google Drive API calls
            └─ WebhookRepository          — POST to webhook URL
       ├─ DailyExportWorker (WorkManager, daily/weekly)
       └─ Every2HoursWebhookWorker (WorkManager, every 2 hours)
```

### Key architectural decisions

- **TypeHandler pattern** (HealthConnectRepository): Each health data type is registered with a `TypeHandler` that knows its record class, time selector, extraction logic, and record updater. This enables data-type-agnostic batch processing — one API call per type for the entire period, grouped by day.
- **Manager classes** (DriveManager, WebhookManager, ScheduleManager): Extracted from ExportViewModel to keep it focused on UI state orchestration. Each manager owns its state flow and persistence.
- **ExportDataUseCase**: Encapsulates the complete export workflow as a `Flow<ExportStep>`, making it testable and separable from ViewModel lifecycle.
- **Separate UI components**: Each card (DriveStatus, Webhook, Schedule, DataType, DateRange, DataSource, ExportedFiles, ExportSummary) is in its own file under `ui/components/`.

### Project structure

```
healthconnect-export/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/com/healthconnect/export/
│       │   ├── MainActivity.kt
│       │   ├── data/
│       │   │   └── DataModels.kt       # DailyHealthRecord, ExportConfig, enums, mappers, KNOWN_SOURCE_PACKAGES
│       │   ├── usecase/
│       │   │   └── ExportDataUseCase.kt # Export workflow (Flow<ExportStep>)
│       │   ├── repository/
│       │   │   ├── HealthConnectRepository.kt  # TypeHandler-based batch reading
│       │   │   ├── LocalExportRepository.kt    # JSON file I/O
│       │   │   ├── GoogleDriveRepository.kt    # Drive API (upload/list/delete)
│       │   │   └── WebhookRepository.kt        # POST with retry
│       │   ├── util/
│       │   │   └── LocaleManager.kt
│       │   ├── viewmodel/
│       │   │   ├── ExportViewModel.kt    # UI state orchestration
│       │   │   ├── DriveManager.kt       # Drive sign-in/sync/sign-out
│       │   │   ├── WebhookManager.kt     # Webhook send/test
│       │   │   └── ScheduleManager.kt    # Schedule/cancel exports
│       │   ├── ui/
│       │   │   ├── ExportScreen.kt
│       │   │   ├── theme/
│       │   │   │   └── AppTheme.kt
│       │   │   └── components/
│       │   │       ├── DataSourceCard.kt
│       │   │       ├── DataTypeCard.kt
│       │   │       ├── DateRangeCard.kt
│       │   │       ├── DriveStatusCard.kt
│       │   │       ├── ExportedFilesCard.kt
│       │   │       ├── ExportSummaryCard.kt
│       │   │       ├── JsonViewerDialog.kt
│       │   │       ├── ScheduleCard.kt
│       │   │       └── WebhookCard.kt
│       │   └── worker/
│       │       ├── DailyExportWorker.kt
│       │       └── Every2HoursWebhookWorker.kt
│       ├── main/res/
│       │   ├── values/strings.xml              # English (136 strings)
│       │   ├── values-ru/strings.xml           # Russian (136 strings, полный перевод)
│       │   ├── values/themes.xml
│       │   └── xml/health_connect_permissions.xml
│       └── test/java/com/healthconnect/export/
│           ├── data/
│           │   ├── HumanReadableMapperTest.kt
│           │   └── DataModelsSerializationTest.kt
│           ├── repository/
│           │   ├── GoogleDriveRepositoryTest.kt
│           │   ├── LocalExportRepositoryTest.kt
│           │   └── WebhookRepositoryTest.kt
│           ├── ui/
│           │   ├── DateRangeCardTest.kt
│           │   └── HighlightJsonSyntaxTest.kt
│           ├── usecase/
│           │   └── ExportDataUseCaseTest.kt
│           ├── util/
│           │   └── LocaleManagerTest.kt
│           ├── viewmodel/
│           │   └── ExportViewModelTest.kt
│           └── worker/
│               ├── DailyExportWorkerTest.kt
│               └── Every2HoursWebhookWorkerTest.kt
├── badges/                          # Coverage badge SVGs (auto-committed by CI)
├── build                            # Build script (bash)
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── scripts/
│   └── locale-validator.py          # Validates translation completeness
├── AGENTS.md                        # This file
└── README.md
```

---

## Data flow

```
UI → ViewModel → ExportDataUseCase (Flow<ExportStep>)
                → HealthConnectRepository (read records, batch mode via TypeHandler)
                → LocalExportRepository (save JSON)
                → DriveManager → GoogleDriveRepository (sync, optional)
                → WebhookManager → WebhookRepository (POST, optional)

Every2HoursWebhookWorker (every 2h)
  → HealthConnectRepository (read today)
  → WebhookRepository (POST, no local save / no Drive)
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

**Test files (323 tests total):**

| File | Tests | Scope |
|---|---|---|
| `WebhookRepositoryTest.kt` | 39 | `sendRecords()` via local HTTP server: success (200/201/204), error (400/403/500), network exception, Bearer auth (token/null/blank/special chars), headers, JSON body, errorstream null. `isValidWebhookUrl()` (19). |
| `DataModelsSerializationTest.kt` | 33 | Roundtrip serialization: DailyHealthRecord (5), ExportConfig (4), ExportFrequency (4), HealthDataType (2), ExportSummary (3), helper functions (3), edge cases (4), SpeedData (5), SerialName verification, sourceDisplayName |
| `HighlightJsonSyntaxTest.kt` | 31 | JSON syntax highlighting: strings, numbers (int/float/sci), booleans, null, nested objects, arrays, escaped quotes, adjacent tokens, realistic health record |
| `HumanReadableMapperTest.kt` | 27 | 8 mapper functions: bodyPositionToString, specimenSourceToString, mealTypeToString, sleepStageToString, measurementLocationToString, menstruationFlowToString, nutritionMealTypeToString, exerciseTypeToString |
| `DailyExportWorkerTest.kt` | 25 | `doWork()` (14): success, already exported, empty, exceptions, config defaults / `schedule()` (4): daily, weekly, manual, cancel / webhook auth test |
| `LocalExportRepositoryTest.kt` | 24 | File operations: getExportDirectory (3), getFilenameForDate (2), isExported (3), saveDailyRecord (3), saveRecords (2), listExportedFiles (6), cleanupOldExports (4), deleteExport (2) |
| `GoogleDriveRepositoryTest.kt` | 23 | Google Drive sync: upload (success, delete exception, special chars), download, list (folder found/not found, error after folder), delete, sign in/out, scopes |
| `Every2HoursWebhookWorkerTest.kt` | 18 | doWork (happy path, blank URL, exceptions), schedule/cancel, constants |
| `ExportDataUseCaseTest.kt` | 15 | Export steps flow: permissions (granted/denied), health check (available/not available/installed), progress, webhook, Drive sync, complete |
| `ExportViewModelTest.kt` | 13 | UI state: loading, export, error, permissions, schedule, data sources, Drive sign-in, webhook test |
| `LocaleManagerTest.kt` | 12 | localeDisplayName (all branches + edge cases), saveLocale, getSavedLocale |
| `DateRangeCardTest.kt` | 8 | DateRangeCard UI: presets, custom dates, picker interaction (Compose UI tests) |

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

| Counter | Minimum |
|---|---|
| LINE | ≥ 30% |
| BRANCH | ≥ 12% |
| INSTRUCTION | ≥ 15% |
| CLASS | ≥ 45% |

### Per-package rules

| Package | Counter | Minimum |
|---|---|---|
| `com.healthconnect.export.worker.*` | LINE | ≥ 95% |
| `com.healthconnect.export.data.*` | LINE | ≥ 70% |
| `com.healthconnect.export.viewmodel.*` | LINE | ≥ 65% |
| `com.healthconnect.export.util.*` | LINE | ≥ 50% |
| `com.healthconnect.export.repository.*` | LINE | ≥ 35% |

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
git tag v1.2
git push origin v1.2
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
- **State management**: `MutableStateFlow` + `update {}` pattern
- **Managers**: DriveManager, WebhookManager, ScheduleManager — own their state flows and persistence, injected into ViewModel
- **UseCase**: ExportDataUseCase — single `Flow<ExportStep>` workflow
- **Serialization**: `kotlinx.serialization` with `@Serializable`, `@SerialName`
- **Background**: WorkManager `CoroutineWorker`
- **Network**: `HttpURLConnection` in `Dispatchers.IO`
- **File format**: `health_YYYY-MM-DD.json`
- **UI strings**: All via `stringResource()` — English and Russian (values-ru/)
- **No separate Application class** — uses AndroidX Startup
- **Data source filtering**: `filterByPreferredOrigin()` picks the best data source per type (preferred package → most records → all)

---

## Scheduling

- **Manual**: Export on button press
- **Daily**: 24h interval via WorkManager `PeriodicWorkRequest`
- **Weekly**: 168h interval
- **Every 2 hours**: Optional webhook-only sending of current day's data (checkbox in Schedule section)
- Default: **Daily** (set at app startup)
- Policy: `ExistingPeriodicWorkPolicy.KEEP` (main), `UPDATE` (2-hour webhook)

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
        .requestIdToken(BuildConfig.GOOGLE_CLIENT_ID)
        .requestScopes(Scope(DriveScopes.DRIVE_FILE))
        .build()
}
```

**Важно:**
- `requestIdToken()` использует **Web Client ID** из `BuildConfig.GOOGLE_CLIENT_ID` — это обязательно для Google Sign-In
- Android Client ID с SHA-1 остаётся в Google Cloud Console для верификации приложения
- Scope `DRIVE_FILE` даёт доступ к файлам, созданным этим приложением

---

## Webhook

### Request

| Attribute | Value |
|---|---|
| **Method** | POST |
| **Content-Type** | `application/json` |
| **Authorization** | `Bearer <token>` (optional) |
| **Timeout** | 15 seconds |
| **Retry** | 1 retry after 1s on 5xx/network errors |
| **URL validation** | Client-side (red highlight on invalid URL) |

### Payload structure

JSON body is wrapped in a `messages` envelope:

```json
{
  "messages": [
    {
      "date": "2026-05-23",
      "steps": { "total_steps": 12453, "records_count": 480 },
      "heart_rate": { "avg_bpm": 72.5, "min_bpm": 55, "max_bpm": 142, "records_count": 18 },
      "sleep": { "total_duration_minutes": 420, "records_count": 1 },
      "metadata": {
        "app_version": "1.0.0",
        "export_timestamp": "2026-05-23T23:00:00",
        "timezone": "Europe/Moscow"
      }
    }
  ]
}
```

Each `messages` element is a `DailyHealthRecord` — one per exported day. Backed by `WebhookPayload` data class in `WebhookRepository.kt`.

Works with both manual and scheduled exports.

---

## Localization

- **Languages**: English (default), Russian
- **Locale switching**: Via Language icon in app bar → System / English / Russian
- **Locale persistence**: Saved to SharedPreferences, Activity recreates on change
- **Coverage**: 136 string resources in each locale, all format placeholders match
- **Validation**: `scripts/locale-validator.py` checks for missing translations
