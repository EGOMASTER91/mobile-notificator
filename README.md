# PrețRadar

[English](README.md) · [Română](README.ro.md)

An Android app for finding products in Romanian online stores, comparing verified offers and receiving local price-drop alerts.

## Features

- Search Altex, eMAG, ForIT, ITGalaxy and evoMAG. Compari.ro is optional and disabled by default.
- Match the requested model and variant; similar products are excluded.
- Follow products, set price thresholds and compare store offers.
- Browse **Hot** deals by category and minimum discount.
- Keep local price history and alerts; receive notifications outside the app.
- Battery-conscious WorkManager checks, every six hours by default, with optional Wi-Fi only and low-battery protection. Android may delay checks; reopening is necessary after force-stop.
- Light/dark themes and fictional demonstration products.

Store access and offers depend on public pages, availability and access restrictions. No CAPTCHA bypass, store login or application backend is included. The current app interface is Romanian; project documentation and release notes are available in English and Romanian.

## Install

Download the signed APK from [Releases](https://github.com/EGOMASTER91/mobile-notificator/releases). Android 10 or later is required. Allow installation from the browser/file manager you use. On Android 13+, grant notification permission to receive alerts.

**Existing debug installation:** the public release uses a separate release signing key and cannot directly replace an APK signed with the development key. Uninstalling deletes local products, history and preferences. Keep the debug installation if you need those records; there is currently no export/import migration.

## Build and verify

Use Android Studio or JDK 21 with Android SDK 36. Configure your own SDK locally; `local.properties` is excluded from Git.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleRelease :app:writeSecurityDependencies
python3 tools/security_audit.py app/build/reports/security/dependencies.json
python3 tools/publication_audit.py
```

On Windows use `gradlew.bat`. The release build is optimized and unsigned by default. Sign it with your own private key, kept outside Git. Never publish keystores or passwords.

GitHub Actions runs unit tests, lint, builds, OSV dependency checks, Gitleaks and publication metadata checks. A separate Android emulator job runs deterministic device tests. Store-dependent live tests are opt-in and skipped in CI.

## Privacy and security

Products, history and preferences remain in the Android application sandbox. Search requests, product URLs and image requests go directly to store/CDN servers; their public JavaScript pages may contact other HTTPS services. There is no app account, analytics SDK or payment storage. See [SECURITY.md](SECURITY.md) and [publication review](security/PUBLICATION-2026-10-10.md).

This public repository has a new neutral Git history. It contains the main application, regression tests and build/security tools, without local SDK paths, old private history, signing secrets, emulators or build output. Demo images are AI-generated, with nonessential provenance metadata removed.

## Keep the workspace small

After builds and your test emulator have stopped, run `./tools/clean-workspace.ps1` in PowerShell. It removes temporary build files and project-local test tools while keeping source, Git history, local signing files and the current release APK. Build files are regenerated when needed. Tests are source code only and do not ship in the release APK.
