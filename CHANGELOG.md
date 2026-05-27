# Changelog

All notable changes to HealthConnect Export will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.3] — 2026-05-27

### Added
- **Webhook:** JSON payload format changed to `{"messages": [...]}` envelope via `WebhookPayload` data class
- **UI:** Export progress with per-day progress bar (read/save phases)
- **UI:** Cancel export button during active export
- **Docs:** README and AGENTS.md updated with webhook payload format specification

### Changed
- **Export:** Day-by-day reading with progress tracking instead of bulk read
- **Viewmodel:** Export cancellation support via `Job.cancel()`, progress state fields
- **Strings:** Added progress and cancel-related string resources (EN + RU)

## [1.2] — 2026-05-27

### Added
- **Tests:** `LocalExportRepositoryTest` — 24 file operation tests (save, list, cleanup, isExported)
- **Tests:** `HealthConnectRepositoryTest` — expanded from 13 to 53 tests (readDay with Sleep/Weight/Calories/Speed/Menstruation/Exercise/Hydration, pagination edge cases)
- **Tests:** `GoogleDriveRepositoryTest` — expanded with 4 edge cases (delete exception, list exception, scopes, special chars)
- **Tests:** `WebhookRepositoryTest` — rewritten from 13 to 39 tests: local HTTP server via `ServerSocket(0)` replaces `mockStatic(URL)`
- **Tests:** `ExportDataUseCaseTest` — 15 use case tests
- **Tests:** `ExportViewModelTest` — 2 new tests
- **Tests:** `DailyExportWorkerTest` — expanded from 14 to 25
- **Tests:** `LocaleManagerTest` — expanded to 12 tests
- **Tests:** Coverage improvements: `repository` ~13% → **~55%**, total 295 tests

### Changed
- **Coverage gate thresholds:** `repository` LINE minimum raised to **≥ 35%**
- **README.md:** Updated test suites table with new files and counts
- **AGENTS.md:** Synced test structure and file listings

## [1.1] — 2026-05-26

### Added
- **CI/CD:** JaCoCo coverage gate with 9 rules (4 global + 5 per-package)
- **CI/CD:** Coverage badge auto-generated on push to `main`
- **CI/CD:** ktlint check integrated into pipeline
- **Lint:** ktlint plugin added, `ktlintFormat` applied
- **Webhook:** Bearer auth support, URL validation
- **Tests:** `DataModelsSerializationTest` — 36 roundtrip serialization tests
- **Tests:** `DailyExportWorkerTest` — 14 WorkManager tests
- **Tests:** `HumanReadableMapperTest` — expanded to 33 tests
- **Docs:** README updated with coverage gate, badge, CI/CD pipeline
- **Docs:** `AGENTS.md` rewritten with full project documentation
- **Docs:** `CHANGELOG.md` — this file

### Changed
- **API upgrade:** Android API 37, Gradle 9.4.1, AGP 9.2.0, Kotlin 2.3.21
- **UI:** Jetpack Compose UI refactor, themes, animations
- **Build:** JaCoCo CSV report enabled for badge generator

### Fixed
- **Data:** Pagination fixes in `readAllPages`, `Double?` safety in SpeedData
- **Fixes:** 17 Health Connect API fixes, MIUI permission workarounds, `readWeight` fix

### Localization
- **Russian (ru):** UI strings translated

## [1.0] — Initial release

### Added
- Daily JSON export of 20 Health Connect data types
- Local storage in `health_YYYY-MM-DD.json` format
- Scheduled export via WorkManager (daily / weekly)
- Google Drive sync with OAuth 2.0
- Webhook delivery (POST JSON to URL)
- Date range selection (7/30 days or custom)
- Material3 UI with Jetpack Compose
- CI/CD: lint, unit tests, JaCoCo coverage, debug & release APKs
- GitHub Release with auto-changelog

---

[1.3]: https://github.com/kas-cor/healthconnect-export/releases/tag/v1.3
[1.2]: https://github.com/kas-cor/healthconnect-export/releases/tag/v1.2
[1.1]: https://github.com/kas-cor/healthconnect-export/releases/tag/v1.1
[1.0]: https://github.com/kas-cor/healthconnect-export/releases/tag/v1.0
