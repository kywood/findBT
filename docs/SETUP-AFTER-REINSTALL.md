# 빌드 PC 다시 설치한 뒤 (Nearo / findBT)

빌드 PC를 포맷하거나 새 PC로 옮겼을 때 빌드와 출시를 다시 이어 가는 순서입니다. 비밀 값(비밀번호, AdMob ID)은
이 문서에도 git에도 없습니다. 이 값들은 따로 보관한 백업에서 가져옵니다.

## 0. 포맷 전에 백업할 것 (git에 없음)

| 무엇 | 예전 위치 | 왜 |
|---|---|---|
| 업로드 키스토어 `gamenuri.jks` (PKCS12, 별칭 `gamenuri`) | `F:\project\keys\gamenuri.jks` | **없으면 Play에 업데이트를 올릴 수 없습니다.** USB와 클라우드에도 복사해 두세요 |
| 키스토어 비밀번호, 키 비밀번호 | 사용자만 앎 | 비밀번호 관리자에 보관 |
| `local.properties`의 값 (아래 2번의 키) | `E:\project_rel\wood\android\findBT-build\local.properties` | AdMob ID 4개, 지원 이메일 |
| 출시본 `app-release.aab`, `mapping.txt` (2.0.0) | `E:\project_rel\wood\android\findBT-release\2.0.0\` | 크래시 분석(mapping), 같은 빌드 다시 올리기 |
| 스토어 캡처 원본 | `findBT\.design-tools\store-raw`, `store-final` | 다시 찍지 않아도 됨 |
| 검토용 캡처와 영상 | `findBT\.design-review\`, `findBT-shots\` | 선택. 공유 폴더에 올린 것은 다시 받을 수 있음 |

키스토어 인증서 확인값: SHA-256 `1A:9C:49:12:1B:63 … :1B:8F`, `O=gamenuri, OU=lab, C=KR`, 유효기간 2222-04-09까지.

## 1. 설치할 것

- Android Studio (AGP 9.2를 지원하는 최신 안정판)
- JDK 17 이상. 프로젝트는 Java 17 바이트코드로 빌드합니다. Android Studio에 든 JBR을 써도 됩니다
- Android SDK: platform 36 (compileSdk/targetSdk 36, minSdk 26), build-tools 36, platform-tools(adb)
- Git, GitHub 로그인(`gh auth login` 또는 Git Credential Manager)
- 선택: bun (스토어 이미지 키트 `store-kit` 실행용. Node/npm 대신 `bun install` 후 `bun final.js`)
- NDK는 필요 없습니다

빌드 도구 버전은 저장소에 고정돼 있습니다: Gradle wrapper 9.4.1, AGP 9.2.1, Kotlin 2.2.10, Compose BOM 2026.02.01
(`gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`). 따로 설치하지 않아도 첫 빌드 때 받습니다.

## 2. 저장소 받기

```
git clone https://github.com/kywood/findBT.git E:\project_rel\wood\android\findBT
cd E:\project_rel\wood\android\findBT
git checkout develop
```

빌드 전용 worktree를 따로 두려면: `git worktree add ..\findBT-build develop`. 일이 끝나고 푸시하면 worktree는 바로 지웁니다.

`local.properties` (git에서 제외, 저장소 루트에 만들기). 키 이름만 적습니다. 값은 백업에서 넣으세요:

```properties
sdk.dir=C:/Users/<사용자>/AppData/Local/Android/Sdk

# 릴리스 서명 (#35). 슬래시(/) 경로로
nearo.signing.storeFile=F:/project/keys/gamenuri.jks
nearo.signing.keyAlias=gamenuri
nearo.signing.storePassword=
nearo.signing.keyPassword=

# 실제 AdMob ID. 없거나 테스트 ID면 릴리스 빌드가 멈춥니다. debug/benchmark는 Google 테스트 ID를 씁니다
nearo.admob.appId=
nearo.admob.banner=
nearo.admob.interstitial=
nearo.admob.rewarded=

# 설정 › 문의에 표시
nearo.supportEmail=

# 선택 기능 (기본 꺼짐)
# nearo.feature.earbudTone=true       # 이어폰 찾기 소리 (#189)
# nearo.feature.premiumPurchase=true  # 인앱 결제 (2.0에서는 끔, #205)
```

서명 값은 환경 변수로도 줄 수 있습니다: `NEARO_SIGNING_STORE_FILE`, `NEARO_SIGNING_STORE_PASSWORD`,
`NEARO_SIGNING_KEY_ALIAS`, `NEARO_SIGNING_KEY_PASSWORD`.

## 3. 빌드 확인

```
gradlew spotlessCheck assembleDebug assembleBenchmark testDebugUnitTest
python scripts/check-strings.py .
```

릴리스 (서명 확인 먼저):

```
gradlew :app:signingReport          # Variant release의 SHA-256이 위 확인값과 같은지
gradlew clean :app:bundleRelease    # app\build\outputs\bundle\release\app-release.aab
```

나머지 점검은 [RELEASE.md](RELEASE.md)를 따릅니다. 출시본은 저장소 밖 `findBT-release\<버전>\`에 AAB와 `mapping.txt`를 함께 둡니다.

## 4. 테스트 폰 (adb)

- Galaxy A50 (Android 11, 시리얼 `R59N300DBHH`): 기본 테스트 폰. Android 11이라 BLE 스캔에 위치가 켜져 있어야 합니다
- Galaxy Z Flip3 (`R5CRB1C9VSH`): 사용자 개인 폰. 사용자가 정한 때만
- 설치는 항상 시리얼을 지정합니다: `adb -s R59N300DBHH install -r app\build\outputs\apk\debug\app-debug.apk`. `gradlew installDebug`는 연결된 모든 기기에 깔리니 쓰지 않습니다
- 패키지: release `com.gamenuri.btscanner24`, debug `.debug`(제목 "Nearo Debug"), benchmark `.benchmark`(제목 "Nearo", 데모 모드 포함). 스토어 캡처는 benchmark로
- 데모 모드(debug/benchmark만): `adb shell am start -n <패키지>/com.gamenuri.btscanner24.MainActivity --es demo nearby|map|devices|settings|find ...` (`--es demo_locale ko`, `--ez demo_ads true`, `--ei demo_left_behind 8` 등)

## 5. git에 넣지 않는 것

빌드 산출물(APK/AAB, `build/`), 캡처와 영상, 키스토어, `local.properties`, `google-services.json`은 커밋하지 않습니다(#191).
로컬에서만 쓰는 폴더는 `.git/info/exclude`에 넣습니다:

```
.design-tools/
.design-review/
```

스토어 이미지 키트 원본은 프로젝트 공유 폴더 `design/v3/store/store-kit/`에 있습니다. `.design-tools\store-kit`로 복사해 씁니다.
