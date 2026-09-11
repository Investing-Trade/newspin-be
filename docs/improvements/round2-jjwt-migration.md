# 라운드 2. jjwt 0.11.5 → 0.12.x 마이그레이션 (I-17)

- **분류**: 개선 (라이브러리 버전/기술 부채)
- **이슈 ID**: I-17
- **브랜치**: `fix/i17-jjwt-migration`
- **상태**: ✅ 완료
- **기간**: 2026-09-11

---

## ① 문제

`JwtTokenProvider`(access/refresh 토큰 발급·검증 전체)가 `jjwt` 0.11.5의 deprecated API를
사용 중이었다: `Jwts.builder().setSubject()/.setExpiration()/.setId()`,
`Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody()`,
`signWith(key, SignatureAlgorithm.HS256)`. 0.11.x 는 유지보수 모드고, 이후 메이저 버전에서
제거될 API라 언젠가는 컴파일이 깨질 부채였다. 동작 자체에는 영향이 없어 심각/위험 항목보다
우선순위가 낮아 라운드 1에서는 범위 밖으로 미뤄뒀다.

## ② 원인 분석

- `build.gradle:42-44` — `jjwt-api/impl/jackson` 전부 `0.11.5` 고정.
- `JwtTokenProvider.java` 전역에서 빌더 API(`setXxx`)와 파서 API(`parserBuilder`/`parseClaimsJws`/
  `getBody`)를 사용. 이 프로젝트에서 jjwt 를 쓰는 곳은 이 클래스 하나뿐(`grep` 확인).

## ③ 해결 방안 검토

| 옵션 | 장점 | 단점 | 채택 |
| --- | --- | --- | --- |
| A. 그대로 유지 | 리스크 0 | 기술 부채 누적, 이후 Spring Boot/보안 패치 시 강제 업그레이드 가능성 | |
| B. 0.12.x 로 갱신 + API 전면 교체 | deprecated API 제거, `signWith(key)` 로 키 타입에서 알고리즘 자동 추론 | 빌더/파서 API 명칭이 전부 바뀌어 손이 감 | ✅ |

## ④ 구현

- `build.gradle`: `jjwt-api/impl/jackson` `0.11.5` → `0.12.6`.
- `JwtTokenProvider`:
  - 발급: `Jwts.builder().setSubject(...)` → `.subject(...)`, `.setExpiration(...)` → `.expiration(...)`,
    `.setId(...)` → `.id(...)`, `.setIssuedAt(...)` → `.issuedAt(...)`.
    `.signWith(key, SignatureAlgorithm.HS256)` → `.signWith(key)` — 0.12.x 는 `SecretKey` 타입에서
    알고리즘(HS256)을 자동으로 추론하므로 `SignatureAlgorithm` import 자체를 제거.
  - 검증/파싱: `Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody()`
    → `Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload()`.
    이 과정에서 `key` 필드 타입을 `java.security.Key` → `javax.crypto.SecretKey` 로 좁힘
    (`verifyWith(SecretKey)` 오버로드가 요구, `Keys.hmacShaKeyFor(...)`가 이미 `SecretKey`를 반환해 자연스럽게 좁혀짐).
- 나머지 로직(리프레시 토큰 Redis 키 구조, 회전, 예외 처리)은 변경 없음 — 순수 API 갱신.

```java
// before (0.11.5)
Jwts.builder().setSubject(username).setExpiration(expireDate)
    .signWith(key, SignatureAlgorithm.HS256).compact();
Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();

// after (0.12.6)
Jwts.builder().subject(username).expiration(expireDate).signWith(key).compact();
Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
```

## ⑤ 검증

- `./gradlew compileJava` — 수정 1회로 바로 통과(추가 컴파일 오류 없음).
- `dev` 프로필(docker-compose MySQL/Redis)로 실기동 후 실제 HTTP 로 전 구간 확인:
  - 회원가입 → 로그인 → access token 발급 → `GET /user/me` 200(claim 파싱·`ROLE_USER` 권한 정상)
  - `POST /user/refresh` → 새 access/refresh 발급(회전)
  - 회전된(사용한) refresh token 재사용 → `C005`(정상 거절, 기존 계약 유지)
  - `POST /user/logout` → 200
- `AuthRefreshTest`(CI, Testcontainers) — 멀티 디바이스·토큰 회전 케이스. 로컬은 Docker
  Desktop 이슈로 컨텍스트 로드 자체가 실패(S0에 기록된 기존 제약, 이 변경과 무관) → 위 실기동
  E2E로 대체 검증, 최종 확인은 CI.

| 지표 | Before | After |
| --- | --- | --- |
| jjwt 버전 | 0.11.5 (deprecated API 사용) | 0.12.6 |
| 발급/파싱 API | `setXxx`, `parserBuilder`, `parseClaimsJws().getBody()` | `subject()`/`expiration()`, `parser().verifyWith()`, `parseSignedClaims().getPayload()` |
| 서명 알고리즘 지정 | `signWith(key, SignatureAlgorithm.HS256)` (명시) | `signWith(key)` (키 타입에서 자동 추론) |
| 기능 동작(회전/멀티 디바이스/거부) | 동일 | 동일 (회귀 없음) |

## ⑥ 회고

- 순수 라이브러리 API 갱신이라도 실제로 로그인/리프레시를 태워보기 전엔 "컴파일 됐다"만으로
  안심할 수 없다 — 서명·검증 양쪽 다 동작해야 토큰 발급-검증 사이클이 성립하므로 실기동 E2E로
  전 구간을 확인.
- jjwt 0.12.x 는 `signWith(Key)` 만으로 알고리즘을 키 타입에서 추론해 API가 오히려 더 단순해졌다
  (`SignatureAlgorithm` import 가 아예 필요 없어짐) — 메이저 업그레이드가 항상 복잡해지는 방향만은
  아니라는 사례.
- 이걸로 알려진 22개 이슈 + 라운드 2 항목(I-16, I-17) 전부 해소. 남은 것은 S-3(데이터 파이프라인
  영역, 범위 밖으로 명시)뿐.
