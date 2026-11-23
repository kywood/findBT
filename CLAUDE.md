# CLAUDE.md

Android app "Nearo: Bluetooth Finder" (package `com.gamenuri.btscanner24`), renewal of an already-released app aimed at a global launch.

## Must follow
- Code convention: [docs/CONVENTION.md](docs/CONVENTION.md)
- Git workflow and message format: [docs/GIT_CONVENTION.md](docs/GIT_CONVENTION.md)
  - Every change: GitHub issue → branch `<type>/<issue>-<slug>` from `main` → PR whose body has `Closes #<issue>`.
  - Commit: `<type>(<scope>): <한국어 요약> (#<issue>)`.
- Shared functionality goes in `core/*` modules and is reused by `feature/*`. Features never depend on each other.
- All user-visible text in `strings.xml`; base language English, plus `values-ko` (later ja, es, pt, de).
- Never change `applicationId`; bump `versionCode` for every release; never commit signing keys or `google-services.json`.

## Modules
`:app` → `:feature:scan` → `:core:ui`, `:core:ble` → `:core:designsystem`, `:core:model`

## Build
- `./gradlew spotlessCheck assembleDebug testDebugUnitTest` before every PR (`spotlessApply` auto-fixes formatting)
- The cloud container cannot reach Google Maven: build on the user's PC.
- AGP 9.x with built-in Kotlin (no `kotlin-android` plugin), Gradle 9.x, JDK 17, Compose BOM, version catalog in `gradle/libs.versions.toml`.
