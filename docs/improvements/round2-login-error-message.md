# 라운드 2. 로그인 실패 에러 메시지 (I-21)

- **분류**: 개선 (API 계약 품질)
- **이슈 ID**: I-21 (신규 발견 — `newspin-web` 실사용 테스트 중 발견)
- **브랜치**: `fix/login-error-message`
- **상태**: ✅ 완료
- **기간**: 2026-09-11

---

## ① 문제

`newspin-web`에서 FE 전 페이지에 "BE가 주는 실제 에러 메시지를 그대로 보여주자"는
개선(라운드 2 FE 작업)을 적용하던 중, 로그인 페이지만 제외해야 했다 — 잘못된 비밀번호로
로그인하면 BE가 `500 "서버 내부 오류가 발생했습니다."`를 반환했기 때문이다. 클라이언트
입력 문제(비밀번호 틀림)인데 서버 오류처럼 보이는 응답.

## ② 원인 분석

`UserService.signIn`이 `AuthenticationManager.authenticate(...)`를 호출하고, 자격 증명이
틀리면 Spring Security가 `BadCredentialsException`(`AuthenticationException`의 하위)을
던진다. `GlobalExceptionHandler`에 이 타입을 위한 핸들러가 없어 catch-all
(`@ExceptionHandler(Exception.class)`)로 떨어져 `ErrorCode.INTERNAL_SERVER_ERROR`(500)로
응답했다. 존재하지 않는 이메일도 `CustomUserDetailsService.loadUserByUsername`이 던지는
`UsernameNotFoundException`(마찬가지로 `AuthenticationException`의 하위)이라 같은 경로로 500이 났다.

## ③ 해결 방안 검토

| 옵션 | 장점 | 단점 | 채택 |
| --- | --- | --- | --- |
| A. FE 에서만 로그인 실패를 고정 문구로 덮어씀 | 변경 최소 | BE 계약이 여전히 부정확(500), 다른 클라이언트가 붙으면 같은 문제 반복 | |
| B. `AuthenticationException` 전용 핸들러 추가, `401` + 전용 메시지 | 문제를 원인(BE)에서 해결, HTTP 상태 코드도 정확해짐(401) | 계정 존재 여부를 구분해 노출하면 안 됨 — 한 메시지로 통일 필요 | ✅ |

## ④ 구현

- `ErrorCode`: `LOGIN_FAILED("C104", "이메일 또는 비밀번호가 일치하지 않습니다.", HttpStatus.UNAUTHORIZED)` 추가.
- `GlobalExceptionHandler`: `@ExceptionHandler(AuthenticationException.class)` 추가.
  `BadCredentialsException`(비밀번호 틀림)과 `UsernameNotFoundException`(이메일 없음) 모두
  `AuthenticationException`의 하위 타입이라 **한 핸들러가 둘 다 잡고 같은 메시지를 준다** —
  "이메일은 맞는데 비밀번호만 틀렸다"를 노출하지 않는 게 계정 존재 여부를 숨기는 보안 관례에 맞음.

```java
@ExceptionHandler(AuthenticationException.class)
public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException e) {
    log.warn("로그인 실패: {}", e.getMessage());
    ErrorCode errorCode = ErrorCode.LOGIN_FAILED;
    return ResponseEntity.status(errorCode.getStatus())
        .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
}
```

## ⑤ 검증

- `GlobalExceptionHandlerTest`(신규 테스트 추가) — `BadCredentialsException`/
  `UsernameNotFoundException` 둘 다 `401` + `C104` + 동일 메시지로 응답하는 것 확인.
- `dev` 프로필 실기동으로 curl 확인:

| 시나리오 | Before | After |
| --- | --- | --- |
| 틀린 비밀번호 | `500`, "서버 내부 오류가 발생했습니다." | `401`, `C104` "이메일 또는 비밀번호가 일치하지 않습니다." |
| 존재하지 않는 이메일 | `500`, "서버 내부 오류가 발생했습니다." | `401`, `C104` (동일 메시지 — 계정 존재 여부 미노출) |
| 올바른 자격 증명 | `200` | `200` (변화 없음) |

## ⑥ 회고

- FE 쪽에서 "BE 메시지를 믿고 그대로 보여주자"는 개선을 적용하려다 보니 오히려 BE의 부정확한
  에러 계약이 드러났다 — FE/BE 를 같이 붙여서 실사용 테스트하는 게 이런 걸 잡아내는 유일한 방법.
- `AuthenticationException` 하나로 "비밀번호 틀림"과 "이메일 없음"을 같은 메시지로 묶은 건
  의도적이다 — 둘을 구분해서 알려주면 공격자가 이메일 존재 여부를 알아낼 수 있다(계정 열거,
  account enumeration).
