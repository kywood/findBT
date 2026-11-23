# Git 컨벤션 (Nearo / findBT)

기존 저장소(`kywood/findBT`, 기본 브랜치 `main`)의 이력을 이어갑니다.
지금까지의 커밋은 `feat : 설명` / `feat: 설명` 형태의 한국어 메시지였고, 이를 [Conventional Commits](https://www.conventionalcommits.org/ko/v1.0.0/) 형식으로 통일합니다.

## 1. 기본 흐름: 이슈 → 브랜치 → PR → 이슈 자동 종료

1. **이슈 만들기:** 작업 하나당 GitHub 이슈 하나. 템플릿(기능 / 버그)을 씁니다.
2. **브랜치 만들기:** 최신 `main`에서 이슈 번호를 넣어 만듭니다.
3. **커밋:** 작은 단위로, 아래 메시지 형식에 맞춰 커밋합니다.
4. **PR 열기:** 본문에 `Closes #이슈번호`를 넣습니다. 머지되면 이슈가 자동으로 닫힙니다.
5. **머지:** 리뷰 후 **Squash and merge**. 머지 후 브랜치는 삭제합니다.

`main`에 직접 커밋하거나 푸시하지 않습니다.

## 2. 브랜치 이름

```
<type>/<이슈번호>-<짧은-설명>
```

- 영어 소문자와 하이픈만 씁니다.
- 예: `feat/3-find-mode`, `fix/7-crash-bluetooth-off`, `refactor/1-scan-architecture`

| type | 용도 |
|---|---|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변경 없는 구조 개선 |
| `design` | UI, 디자인 시스템, 리소스 |
| `docs` | 문서 |
| `test` | 테스트 추가, 수정 |
| `chore` | 빌드, 의존성, 설정 |
| `release` | 출시 준비 (버전, 스토어 문구) |

## 3. 커밋 메시지

```
<type>(<scope>): <요약> (#이슈번호)

<본문: 무엇을, 왜 바꿨는지. 선택>
```

- type은 위 표와 같습니다. scope는 모듈 이름입니다 (`ble`, `scan`, `designsystem`, `app`, `build`). 없으면 생략.
- 요약은 한국어, 50자 이내, 마침표 없이, "~함 / ~추가" 대신 명령형 또는 명사형으로 씁니다.
- 예:
  - `feat(scan): 블루투스 꺼짐 안내와 켜기 버튼 추가 (#1)`
  - `fix(ble): 스캔 콜백에서 맵 동시 수정 문제 해결 (#4)`
  - `chore(build): versionCode 2로 올림 (#1)`

## 4. PR 규칙

- 제목은 대표 커밋과 같은 형식: `refactor(app): 모듈 분리와 스캔 안정화 (#1)`
- 본문은 `.github/pull_request_template.md`를 따릅니다. `Closes #N`은 필수입니다.
- PR 하나는 이슈 하나. 너무 크면 이슈를 나눕니다.
- 빌드와 단위 테스트(`./gradlew assembleDebug testDebugUnitTest`)가 통과한 뒤 리뷰를 요청합니다.

## 5. 태그와 버전

- 출시 시 `main`에 태그 `v<versionName>`을 붙입니다 (예: `v2.0.0`).
- `versionCode`는 출시마다 1씩 올립니다. 스토어에 올라간 값보다 작으면 업로드가 거부됩니다.
- 서명 키(`*.jks`, `*.keystore`)와 `google-services.json`은 절대 커밋하지 않습니다 (`.gitignore`에 포함).

## 6. 라벨

`feat`, `bug`, `design`, `docs`, `chore`, `priority:high`
