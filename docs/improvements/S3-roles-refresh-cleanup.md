# S3. 권한 접두사 + 리프레시 토큰 키 + 데드코드 (I-6, I-7, I-9, I-18)

- **분류**: 개선 (구조/보안)
- **이슈 ID**: I-6, I-7, I-9, I-18
- **브랜치**: `refactor/roles-refresh-deadcode`
- **상태**: ✅ 구현 완료
- **기간**: 2026-09-09

---

## I-6. 권한 `ROLE_` 접두사

- `CustomUserDetails.getAuthorities()` 가 `"USER"` (접두사 없음) 를 부여 → `hasRole("...")` 는
  내부적으로 `ROLE_` 를 붙여 비교하므로 항상 실패. `SecurityConfig` 의
  `.requestMatchers(DELETE, "/user").hasRole("ADMIN")` 는 사실상 도달 불가 데드코드
  (게다가 `DELETE /user` 컨트롤러 자체가 없음).
- 조치: `getAuthorities()` → `"ROLE_USER"`. `hasRole("ADMIN")` 매처 제거
  (관리자 기능이 없으므로 RBAC 를 새로 만들지 않고 데드 매처만 정리).
- 토큰의 `auth` 클레임도 자연히 `ROLE_USER` 로 바뀌고, `getAuthentication` 파싱과 일관.

## I-7. 리프레시 토큰 키 (멀티 디바이스)

- 기존: `redisUtil.setDataExpire(email, refreshToken, ...)` — 키가 email 단일이라 한 유저가
  여러 기기에서 로그인하면 나중 로그인이 이전 토큰을 덮어씀. 게다가 TTL 로 ms(259,200,000) 를
  s 로 넘겨 사실상 무기한.
- 조치:
  - 키를 `refresh:{email}:{md5(token)}` 로 → 기기별 독립 저장 (멀티 디바이스)
  - TTL `REFRESH_TOKEN_EXPIRE_TIME / 1000` 로 수정 (3일)
  - 로그아웃: `deleteByPattern("refresh:{email}:*")` — 전 기기 무효화
  - 리프레시: `invalidateRefreshToken(old)` 로 회전 — 방금 쓴 토큰만 폐기 후 새로 발급
  - `RedisUtil.deleteByPattern` 추가
- 부수: 이 변경으로 이메일 인증코드(키=email)와 리프레시 토큰이 같은 키를 쓰던 잠재 충돌도 해소.

## I-9. `SimulationSession` 데드코드

- `advanceDay()`, `reset()`, `isActive()`, `isCompleted()` 삭제 — 어디서도 호출되지 않음.
  "다음 날 진행" 로직은 `NextDayService` 가 담당(주말 스킵 포함). 엔티티/서비스 이중 구현 정리.

## I-18. `RedisConfig` 오토컨피그 우회 제거

- `RedisConfig` 가 `@Value` 로 `LettuceConnectionFactory` 를 직접 생성 → Spring Boot 오토컨피그,
  `@ServiceConnection`, actuator health 를 우회. 기본값 없어 환경변수 미설정 시 컨텍스트 로드 실패.
- 조치: 커스텀 팩토리 빈 제거, 오토컨피그된 `RedisConnectionFactory` 주입. `RedisTemplate` 커스터마이징만 유지.
- 결과: `/actuator/health` 에 `redis` 상태 표시, 테스트에서 Redis `@ServiceConnection` 사용 가능.

## 검증

- 로컬 E2E: 기기 A/B 로그인 → 둘 다 refresh 성공(기존엔 A 무효), A 재사용 시 `C005`,
  `/user/me` 200(ROLE_USER), `/actuator/health` `redis UP`.
- `AuthRefreshTest` (CI): 멀티 디바이스 / 토큰 회전.
- 통합 테스트 베이스에 Redis 컨테이너 추가.

## ⑥ 회고

- `hasRole` 은 `ROLE_` 를 자동으로 붙인다 — authority 문자열을 직접 다룰 땐 규약을 맞춰야 한다.
- 세션 저장을 "식별자 하나 = 값 하나" 로 잡으면 멀티 디바이스에서 깨진다. 기기(또는 토큰)별 키가 필요.
- 프레임워크가 해주는 걸 손으로 다시 만들면(=커스텀 커넥션 팩토리) 오토컨피그가 주는 것들(health,
  테스트 지원, 기본값)을 전부 잃는다.
