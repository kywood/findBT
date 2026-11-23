# CLAUDE.md

Android app "Nearo: Bluetooth Finder" (package `com.gamenuri.btscanner24`), renewal of an already-released app aimed at a global launch.

## Must follow
- Code convention: [docs/CONVENTION.md](docs/CONVENTION.md)
- Git workflow and message format: [docs/GIT_CONVENTION.md](docs/GIT_CONVENTION.md)
  - Every change: GitHub issue → branch `<type>/<issue>-<slug>` from `develop` → PR **into `develop`** whose body has `Closes #<issue>`.
  - `develop` is the integration branch. A PR merges into it once the PC build, unit tests and `spotlessCheck` pass and the app runs on a real phone (A50, and Flip3 when connected).
  - `main` only receives one `develop` → `main` PR at the end of a work phase (or a release). Never push to `main` directly.
  - `Closes #N` only auto-closes when the commit reaches `main`, so issues stay open until that PR merges.
  - Commit: `<type>(<scope>): <한국어 요약> (#<issue>)`.
- Shared functionality goes in `core/*` modules and is reused by `feature/*`. Features never depend on each other.
- All user-visible text in `strings.xml`; base language English, plus `values-ko` (later ja, es, pt, de).
- Never send scan data (device names, MAC, RSSI, company, UUIDs, location) to Analytics, Crashlytics or AdMob; aggregate events only (#19).
- Never change `applicationId`; bump `versionCode` for every release; never commit signing keys or `google-services.json`.

## Modules
`:app` → `:feature:scan`, `:feature:finder`, `:feature:devices` → `:core:ui`, `:core:ble`, `:core:data`, `:core:sensors` → `:core:designsystem`, `:core:model`

## Build
- `./gradlew spotlessCheck assembleDebug testDebugUnitTest` before every PR (`spotlessApply` auto-fixes formatting)
- The cloud container cannot reach Google Maven: build on the user's PC.
- AGP 9.x with built-in Kotlin (no `kotlin-android` plugin), Gradle 9.x, JDK 17, Compose BOM, version catalog in `gradle/libs.versions.toml`.
