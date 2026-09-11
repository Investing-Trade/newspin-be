# 라운드 2. validation 에러 응답 표준화 (I-16)

- **분류**: 개선 (API 계약 품질)
- **이슈 ID**: I-16
- **브랜치**: `fix/i16-validation-error-format`
- **상태**: ✅ 완료
- **기간**: 2026-09-11

---

## ① 문제

- `GlobalExceptionHandler.handleMethodArgumentNotValidException` — `@Valid` 검증 실패 시
  어떤 필드가 왜 실패했는지 버리고 항상 `"유효성 검사가 실패했습니다."` 라는 문자열 하나만 반환.
  클라이언트(FE)가 어느 입력란에 에러를 표시해야 하는지 알 방법이 없었다.
- 같은 핸들러가 에러 코드로 `ErrorCode` enum 대신 매직 스트링 `"400"` 을 직접 사용 —
  다른 모든 에러 응답이 `Cxxx`/`Uxxx`/`Sxxx` 체계를 따르는 것과 불일치.
- `ErrorCode.message` 필드가 `final` 이 아님 — `code`/`status` 는 `final`인데 `message` 만
  아니라서, enum 상수(싱글턴)가 우연히 변경 가능한 상태로 남아있었다(현재 setter는 없어 실제
  변경 경로는 없지만, 다른 필드와 불일치하는 잠재적 함정).

## ② 원인 분석

- `src/main/java/.../global/exception/GlobalExceptionHandler.java:20-25` — `FieldError` 목록을
  순회하지 않고 고정 문자열만 반환.
- `src/main/java/.../global/exception/ErrorCode.java:52` — `private String message;` (다른 두 필드는 `final`).

## ③ 해결 방안 검토

| 옵션 | 장점 | 단점 | 채택 |
| --- | --- | --- | --- |
| A. 메시지 문자열만 좀 더 친절하게 수정 | 변경 최소 | 필드별 원인은 여전히 못 알아냄 | |
| B. `ApiResponse.data` 에 `{필드명: 메시지}` 맵을 실어 반환 | 기존 `ApiResponse<T>` 계약(제네릭 data) 그대로 재사용, FE 가 필드 단위로 에러 표시 가능 | 에러 응답에 `data`가 채워지는 첫 사례(기존엔 항상 null) | ✅ |

## ④ 구현

- `ApiResponse<T>`: `error(code, message, data)` 오버로드 추가 (기존 `error(code, message)`는 유지, `data=null`).
- `GlobalExceptionHandler.handleMethodArgumentNotValidException`:
  - `BindingResult.getFieldErrors()` 를 순회해 `LinkedHashMap<String, String>`(필드명→메시지) 구성.
  - 에러 코드를 매직 스트링 `"400"` → `ErrorCode.INVALID_INPUT_VALUE`(`C002`)로 통일.
  - 로그 레벨을 `error` → `warn` (클라이언트 입력 문제는 서버 오류가 아님).
- `ErrorCode.message` → `final` (다른 두 필드와 일관).

```java
// before
return ResponseEntity.status(e.getStatusCode())
    .body(ApiResponse.error("400", "유효성 검사가 실패했습니다."));

// after
Map<String, String> fieldErrors = ...; // {"email": "이메일 형식이 올바르지 않습니다.", ...}
return ResponseEntity.status(errorCode.getStatus())
    .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage(), fieldErrors));
```

## ⑤ 검증

- `GlobalExceptionHandlerTest`(신규, 순수 단위 테스트 — Spring 컨텍스트 없이 핸들러 메서드만 호출):
  필드 2개 오류를 담은 `MethodArgumentNotValidException` → 응답 `data` 에 두 필드 모두 정확한
  메시지로 포함, `code == "C002"` 확인.
- `./gradlew compileJava` 통과. 기존 테스트에 이 핸들러를 검증하는 것이 없어 회귀 없음.

| 지표 | Before | After |
| --- | --- | --- |
| 검증 실패 응답 `message` | 항상 `"유효성 검사가 실패했습니다."` | 동일(요약 메시지 유지) |
| 필드별 원인 | 없음 | `data = {필드명: 메시지}` |
| 에러 코드 | `"400"` (매직 스트링) | `"C002"`(`ErrorCode.INVALID_INPUT_VALUE`) |
| `ErrorCode.message` 가변성 | `final` 아님 | `final` |

## ⑥ 회고

- `ApiResponse<T>` 가 이미 제네릭이었기 때문에, 에러 응답에도 `data`를 실을 수 있는 구조가
  처음부터 있었다 — 안 쓰고 있었을 뿐. 응답 포맷을 새로 만들 필요 없이 기존 계약 안에서 해결.
- FE 는 아직 이 필드별 에러를 소비하지 않지만(현재 폼 검증을 FE 자체에서도 하고 있어 실사용
  빈도는 낮음), 서버가 필드 단위 정보를 주는 것 자체는 즉시 이득 — 프론트가 늦게 필요해지면
  그때 소비하면 됨(응답 구조가 이미 준비돼 있음).
