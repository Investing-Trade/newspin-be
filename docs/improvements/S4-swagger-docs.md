# S4. API 문서(Swagger) 정리

- **분류**: 개선 (문서화)
- **이슈 ID**: 스테이지 4 마감 항목 (신규 이슈 ID 없음)
- **브랜치**: `chore/stage4-swagger-docs`
- **상태**: ✅ 완료
- **기간**: 2026-09-11

---

## ① 문제

컨트롤러 6개, 엔드포인트 21개 전부 `@Tag`/`@Operation` 등 OpenAPI 어노테이션이 **0개**였다.
Swagger UI(`/swagger-ui/index.html`)에 경로만 나열되고 설명이 없어 API 계약을 파악하려면
컨트롤러 소스를 직접 읽어야 했다. 추가로 `SwaggerConfig` 가 `OpenAPI.addSecurityItem(bearer)`
를 전역으로 걸어, `/user/sign-up`·`/user/sign-in` 처럼 인증이 필요 없는 엔드포인트까지
Swagger UI 상 자물쇠 아이콘과 "Authorize" 요구가 붙어 있었다(실제 동작과 문서 불일치).

## ② 원인 분석

- `SwaggerConfig`(`src/main/java/.../global/config/SwaggerConfig.java`)가 `SecurityRequirement`
  를 `OpenAPI` 루트에 붙임 → springdoc 이 모든 경로에 상속.
- `SecurityConfig.PERMIT_ALL_PATTERNS` 와 대조하면 `/user/sign-up`, `/user/sign-in`,
  `/user/refresh`, `/user/email/**`, `/user/password/**` 는 인증 없이 호출 가능한데 문서상으로는
  구분되지 않았음.

## ③ 해결 방안 검토

| 옵션 | 장점 | 단점 | 채택 |
| --- | --- | --- | --- |
| A. 전역 SecurityRequirement 유지, 어노테이션만 추가 | 변경 최소 | 인증 불필요 API 도 잠금 표시 유지 → 문서 부정확 지속 | |
| B. 전역 requirement 제거 + 컨트롤러/메서드별 명시 | `SecurityConfig` 의 실제 인가 규칙과 문서가 1:1로 대응 | 어노테이션 개수 늘어남(공개 API마다 `@SecurityRequirements` 필요) | ✅ |

## ④ 구현

- `SwaggerConfig`: `addSecurityItem` 제거. `bearer` 스킴 정의만 유지, `Info` 에 Authorize 사용법 설명 추가.
- 컨트롤러 6개에 클래스 레벨 `@Tag(name, description)` + (인증이 필요하면) 클래스 레벨
  `@SecurityRequirement(name = "bearer")` 부여.
- `UserController` 는 메서드별로 혼재(로그인 전/후)하므로, 인증 불필요 메서드에만
  `@SecurityRequirements`(빈 배열)를 붙여 클래스 상속을 개별 무효화.
- 엔드포인트 21개 전부에 `@Operation(summary, description)` — I-3(캐시)/I-10(페이지네이션)/
  C-3(가격 서버 검증)/C-5(lookahead 차단)/I-11(리포트 비동기 상태값) 등 이번 스테이지에서
  바뀐 동작을 설명에 반영.

## ⑤ 검증

- `./gradlew compileJava` 통과.
- `dev` 프로필로 기동 후 `GET /v3/api-docs` 확인:
  - 경로 21개 전부 `tags` 채워짐.
  - `/user/sign-up`, `/user/sign-in`, `/user/refresh`, `/user/email/*`, `/user/password/*` →
    `security: []` (공개).
  - 나머지 전부 `security: [{"bearer":[]}]`.
- 응답 형태/동작 변경 없음(어노테이션만 추가) — 기존 테스트 스위트 영향 없음.

## ⑥ 회고

- 어노테이션이 하나도 없던 이유는 초기 개발 단계에서 Swagger 설정만 붙이고 방치된 것으로 보인다.
  전역 보안 요구사항 하나가 21개 엔드포인트의 문서를 통째로 틀리게 만들 수 있다는 점이 눈에 띄었다.
- 남은 과제: 요청/응답 DTO에 `@Schema(description=...)` 를 붙이면 필드 단위 설명까지 채울 수 있지만,
  DTO 개수가 많아 이번 스테이지 범위에서는 제외했다(우선순위: 마감 항목 중 나머지 3개 우선).
