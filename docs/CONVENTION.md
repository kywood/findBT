# 코드 컨벤션 (Nearo / findBT)

이 문서는 널리 쓰이는 안드로이드 표준을 이 프로젝트에 맞게 정리한 것입니다.
기준 문서는 아래 네 가지이며, 이 문서에 없는 내용은 기준 문서를 따릅니다.

- [Kotlin 공식 코딩 컨벤션](https://kotlinlang.org/docs/coding-conventions.html)
- [Android Kotlin 스타일 가이드](https://developer.android.com/kotlin/style-guide)
- [Jetpack Compose API 가이드라인](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-api-guidelines.md)
- [Android 앱 아키텍처 가이드](https://developer.android.com/topic/architecture)와 [Now in Android](https://github.com/android/nowinandroid)의 모듈 구조

## 1. 모듈 구조

공통으로 쓰는 기능은 반드시 `core` 모듈에 만들고, 화면(`feature`)은 그것을 가져다 씁니다.
같은 코드를 두 번째로 복사하려는 순간이 `core`로 옮길 때입니다.

| 모듈 | 역할 | 의존 가능 |
|---|---|---|
| `:app` | Application, MainActivity, 내비게이션, DI 조립 | 모든 모듈 |
| `:feature:<이름>` | 화면 하나의 UI와 ViewModel (`scan`, `finder`, 이후 `settings` 등) | `core:*`만 (다른 feature 금지) |
| `:core:model` | 순수 데이터 모델 (`BleDevice` 등). 안드로이드 UI 의존 금지 | 없음 |
| `:core:ble` | BLE 스캔, 광고 패킷 파싱, 기기 저장소. 앱 전체가 공유 | `core:model` |
| `:core:designsystem` | 테마, 색, 타이포, 공통 컴포넌트 (`StatusBanner` 등) | 없음 |
| `:core:ui` | 여러 화면이 쓰는 UI 조각 (`DeviceCard`, 권한 처리 `rememberBlePermissionState`) | `core:model`, `core:designsystem` |
| `:core:sensors` | 방위(회전 벡터)·걷기 감지 센서와 방향 추정(`BearingEstimator`) | 없음 |
| `:core:data` | 저장소: 설정(DataStore), 이후 내 기기(Room) | `core:model` |

새로 추가할 예정인 공통 모듈: `core:analytics`, `core:testing`.

규칙:
- feature끼리는 서로 의존하지 않습니다. 공유가 필요하면 `core`로 내립니다.
- 외부 라이브러리는 한 모듈 안에 감춥니다. 예: Accompanist 권한은 `core:ui/permission`에서만 씁니다.
- 의존성 버전은 `gradle/libs.versions.toml`에서만 관리합니다. 모듈 간 참조는 `projects.core.ble` 형식(type-safe accessor)을 씁니다.
- 모듈 빌드 설정이 반복되면 `build-logic` convention plugin으로 묶습니다(모듈이 6개를 넘으면 도입).

## 2. 아키텍처

- **UDF(단방향 데이터 흐름):** ViewModel은 `StateFlow<XxxUiState>` 하나를 내보내고, 화면은 이벤트를 ViewModel 함수 호출로 올립니다.
- **UiState:** 화면당 `data class XxxUiState`, 상태 종류는 `sealed interface`로 표현합니다 (`ScanStatus.Idle / Scanning / Error`).
- **데이터 계층:** 플랫폼 콜백은 `callbackFlow`로 감싸 `Flow`로 노출합니다. 수집을 취소하면 자원이 해제되어야 합니다 (`BleScanner.scan()` 참고).
- **스레드 안전:** 공유 상태는 `MutableStateFlow.update {}`로 바꿉니다. `mutableMapOf`를 여러 스레드에서 직접 고치지 않습니다.
- **ViewModel 생성:** 현재는 `BleDependencies.Provider` 기반 수동 DI입니다. Hilt 도입 시 `@HiltViewModel`로 교체합니다.
- **순수 로직 분리:** 정렬, 필터, 파싱처럼 안드로이드 타입이 필요 없는 로직은 `object`나 클래스로 분리해 단위 테스트합니다 (`DeviceListOrdering`, `BleParser`).

## 3. Kotlin 스타일

- 들여쓰기 4칸, 한 줄 최대 120자, 와일드카드 import 금지(`import x.*` 금지).
- 여러 줄 인자 목록과 컬렉션에는 trailing comma를 붙입니다.
- 이름 규칙:
  - 클래스, 인터페이스, enum: `PascalCase` (`BleScanner`, `ScanFailure`)
  - 함수, 변수: `camelCase`
  - 상수: `UPPER_SNAKE_CASE` (`const val`, companion object)
  - 노출용 상태 프로퍼티는 `_state`(private mutable) / `state`(public read-only) 쌍
  - 패키지: 소문자, 모듈 경로와 일치 (`com.gamenuri.btscanner24.core.ble`)
- `!!` 금지. null이 가능한 값은 `?.`, `?:`, `requireNotNull(message)`로 처리합니다.
- 권한이 필요한 API 호출에는 `@SuppressLint("MissingPermission")`를 쓰되, 바로 위에서 실제로 권한을 확인하고 `SecurityException`을 잡습니다.
- 주석은 "왜"를 씁니다. 한국어와 영어 모두 허용하지만 공개 API(KDoc)는 영어로 씁니다.
- 주석 처리된 죽은 코드는 커밋하지 않습니다. 필요하면 Git 이력에서 찾습니다.

## 4. Compose 규칙

- `@Composable` 함수 중 UI를 그리는 것은 `PascalCase` 명사, 값을 반환하는 것은 `camelCase` (`rememberBlePermissionState`).
- 매개변수 순서: 필수 값 → `modifier: Modifier = Modifier` → 선택 값 → 마지막 `content` 람다.
- 모든 공개 Composable은 `modifier` 매개변수를 받고, 가장 바깥 요소에 한 번만 적용합니다.
- 상태는 끌어올립니다(state hoisting). 화면 진입점(`XxxScreen`)만 ViewModel을 알고, 하위 Composable은 값과 람다만 받습니다.
- Flow 수집은 `collectAsStateWithLifecycle()`을 씁니다 (`collectAsState` 금지).
- `LazyColumn`/`LazyRow`의 `items`에는 항상 `key`를 줍니다.
- 색, 글꼴, 간격은 `MaterialTheme`과 `core:designsystem`에서만 가져옵니다. 화면에 색상 값을 직접 쓰지 않습니다.
- 화면 하나에 `@Preview`를 최소 하나 둡니다(밝은 테마, 어두운 테마).

## 5. 리소스와 다국어

- 사용자에게 보이는 모든 문구는 `strings.xml`에 둡니다. 코드에 문자열을 직접 쓰지 않습니다.
- 문자열은 그 문구를 쓰는 모듈의 `res`에 둡니다. 앱 이름(`app_name`)만 `:app`에 있습니다.
- 기본 언어(`values/`)는 영어, 번역은 `values-ko/`, `values-ja/` 등에 둡니다. 새 문자열은 영어와 한국어를 함께 추가합니다.
- 개수는 `plurals`, 문장 안의 값은 위치 인자(`%1$d`, `%2$s`)를 씁니다. 문자열을 `+`로 이어 붙이지 않습니다.
- 날짜와 시간은 기기 설정을 따릅니다 (`DateFormat.getTimeFormat(context)`).
- 리소스 이름: `<화면 또는 역할>_<내용>` (`action_start_scan`, `error_bluetooth_off`).

## 6. BLE와 권한

- 스캔은 화면이 보일 때만 돌립니다. `LifecycleStartEffect`로 멈추고 다시 시작합니다.
- 30초에 5번 넘게 스캔을 시작하면 안드로이드가 막습니다. `BleScanner.startCooldownMillis()`를 확인합니다.
- 블루투스 꺼짐, 미지원, 권한 없음, 실패는 모두 `ScanFailure`로 화면에 알립니다. 조용히 실패하지 않습니다.
- 화면 갱신은 최대 초당 2회입니다 (`sample(500ms)`).
- 권한 요청은 `core:ui`의 `rememberBlePermissionState`로만 합니다. 영구 거부 시 설정 화면으로 보냅니다.

## 7. 테스트

- 위치: 모듈별 `src/test`(JVM 단위 테스트), `src/androidTest`(기기 테스트).
- 이름: `` `무엇을 하면 무엇이 된다`() `` 형태의 백틱 함수명.
- 순수 로직(파서, 정렬, 필터, 거리 구간 계산)은 반드시 단위 테스트를 함께 커밋합니다.
- 코루틴 테스트는 `kotlinx-coroutines-test`의 `runTest`를 씁니다.

## 8. 정적 분석

- 포맷: ktlint (Spotless로 실행, `.editorconfig` 기준, `ktlint_code_style = android_studio`)
  - 확인: `./gradlew spotlessCheck` / 자동 수정: `./gradlew spotlessApply`
- PR 전에 `spotlessCheck`, `assembleDebug`, `testDebugUnitTest`가 모두 통과해야 합니다.
- detekt(Compose 규칙 포함)는 UI 구현 단계에서 추가합니다. CI가 생기면 PR마다 자동 실행합니다.

## 개인정보

- 스캔 데이터(기기 이름, MAC 주소, RSSI, 제조사, 서비스 UUID, 위치)는 Analytics, Crashlytics 커스텀 키·로그, AdMob 어디에도 보내지 않습니다. 이벤트는 집계 값만 보냅니다 (예: `find_started`, `found_in_seconds` 구간). (#19)
