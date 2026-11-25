# Release checklist

How to build the Play Store bundle of Nearo (`com.gamenuri.btscanner24`). Nothing secret is in git:
the keystore, its passwords, the real AdMob IDs and `google-services.json` live on the release PC only.

## 1. Once per PC

Add to `local.properties` (git-ignored), or set the environment variables in brackets:

```properties
# Signing: the existing upload key of com.gamenuri.btscanner24. Never commit the keystore.
nearo.signing.storeFile=C:/keys/nearo-upload.jks      # [NEARO_SIGNING_STORE_FILE]
nearo.signing.storePassword=...                        # [NEARO_SIGNING_STORE_PASSWORD]
nearo.signing.keyAlias=...                             # [NEARO_SIGNING_KEY_ALIAS]
nearo.signing.keyPassword=...                          # [NEARO_SIGNING_KEY_PASSWORD]

# Real AdMob IDs. Without them the build uses Google's test IDs.
nearo.admob.appId=ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX
nearo.admob.banner=ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX
nearo.admob.native=ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX
nearo.admob.interstitial=ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX
nearo.admob.rewarded=ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX

# Shown in Settings > Contact.
nearo.supportEmail=...
```

Use forward slashes in the keystore path. If any of the four signing values is missing, the release
build still runs but the bundle is unsigned and Play will reject it.

## 2. Before every release

- [ ] `develop` is merged into `main` through one PR, and you build from `main`.
- [ ] `versionCode` in `app/build.gradle.kts` is higher than the last uploaded one; `versionName` is set.
- [ ] `applicationId` is still `com.gamenuri.btscanner24`.
- [ ] `./gradlew spotlessCheck testDebugUnitTest` passes and `python3 scripts/check-strings.py .` reports 0 issues.
- [ ] `local.properties` has the signing values and the real AdMob IDs (section 1).
- [ ] `git status` shows no keystore, `local.properties` or `google-services.json` staged.

## 3. Build

```
./gradlew clean :app:bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`.

## 4. Check the bundle

- [ ] Signed with the upload key: `jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab`
      prints "jar verified" and the expected certificate.
- [ ] Not the test ad IDs: the merged manifest
      (`app/build/intermediates/merged_manifests/release/`) has your `ca-app-pub-` app ID, not
      `ca-app-pub-3940256099942544~3347511713`.
- [ ] Size is in the expected range (about 4 MB download per device) and R8 ran
      (`app/build/outputs/mapping/release/mapping.txt` exists; keep it for crash reports).
- [ ] Install the release build on a real phone (`bundletool build-apks --connected-device` or the
      internal testing track) and run: first start and permission, scan, find a device, Found, Settings,
      Premium purchase with a licence tester, restore, one rewarded ad.

## 5. Upload

- [ ] Play Console: internal testing first, then production with a staged rollout.
- [ ] Privacy policy URL is live: https://kywood.github.io/findBT/privacy/
- [ ] Data safety form matches the privacy policy.
- [ ] Tag the commit: `git tag v<versionName> && git push origin v<versionName>`.
