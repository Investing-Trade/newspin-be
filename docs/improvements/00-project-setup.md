# 00. 개선 작업 기반 세팅

- **분류**: 인프라
- **이슈 ID**: I-15(부분), 신규
- **브랜치**: `chore/improvement-groundwork`
- **상태**: 🚧 진행 중
- **기간**: 2026-09-09 ~

---

## ① 문제

개선 작업을 시작하려는데 기반이 없다.

1. **빌드 불가**: `build.gradle` 툴체인이 Java 25인데 개발 머신 JDK는 17. `./gradlew compileJava`가
   `Cannot find a Java installation ... matching {languageVersion=25}` 로 실패. 툴체인 다운로드 저장소도 미설정.
2. **문서 체계 없음**: 개선 과정을 남길 곳이 없고, `.gitignore`가 `/docs`를 제외하고 있어 만들어도 커밋되지 않음.
3. **측정/회귀 방지 수단 없음**: 테스트 1개, CI 없음, 부하 측정 스크립트 없음.

## ② 원인 분석

- `settings.gradle`에 toolchain resolver 플러그인이 없어 Gradle이 JDK를 자동 조달하지 못함.
- `.gitignore` 20번째 줄 `/docs` — 과거 Notion 익스포트 등을 제외하려던 흔적으로 추정.
- 팀이 `DataLoader.java`와 `src/main/resources/data/`를 `.gitignore`에 넣어 시드 파이프라인이 저장소에 없음 (C-2).

## ③ 해결 방안 검토

| 항목 | 옵션 | 채택 | 근거 |
| --- | --- | --- | --- |
| JDK 25 확보 | (A) 개발자가 수동 설치 / (B) Gradle foojay resolver로 자동 프로비저닝 | **B** | clone 후 `./gradlew`만으로 빌드되는 재현성. CI에서도 동일 |
| foojay 버전 | 0.9.0 / 1.0.0 | **1.0.0** | 0.9.0은 Gradle 9.x에서 `IBM_SEMERU` enum 파싱 버그로 빌드 실패 |
| docs 위치 | 레포 `docs/` / 별도 레포 | **레포 `docs/`** | 커밋 히스토리와 나란히 봄 |

## ④ 구현

- `settings.gradle`: `org.gradle.toolchains.foojay-resolver-convention` 1.0.0 추가
- `.gitignore`: `/docs` 제외 항목 삭제
- `docs/` 구조 생성:
  - `README.md` — 진행 현황 대시보드
  - `00-baseline/01-architecture.md`, `02-known-issues.md`, `03-improvement-roadmap.md`
  - `improvements/_TEMPLATE.md`
  - `adr/0001-docs-and-workflow.md`

## ⑤ 검증

| 지표 | Before | After |
| --- | --- | --- |
| `./gradlew compileJava` (로컬 JDK 17) | 실패 (toolchain 없음) | `BUILD SUCCESSFUL` (Temurin 25 자동 다운로드, 58s 최초 1회) |
| 개선 과정 문서 | 없음 / 커밋 불가 | `docs/` 커밋됨 |

## ⑥ 회고

- 레거시 개선의 첫 단계는 "손대는 것"이 아니라 "손댈 수 있게 만드는 것" — 재현 가능한 빌드, 회귀 안전망, 측정 지점을 먼저 확보.
- 기능 수정(예: 가격 검증)이 회귀 없이 동작하는지 증명할 방법이 없으면 개선이 아니므로, 특성화 테스트·측정 인프라를 앞에 둠.
