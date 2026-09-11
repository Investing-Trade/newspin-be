# 라운드 2. 이메일 발송 하드코딩 제거 (I-20)

- **분류**: 개선 (구조 — 커스텀 Bean이 오토컨피그를 가로챔, I-18과 같은 유형)
- **이슈 ID**: I-20 (신규 발견 — 착수 시점 인벤토리엔 없었음)
- **브랜치**: `fix/smtp-configuration`
- **상태**: ✅ 완료
- **기간**: 2026-09-11

---

## ① 문제

`newspin-web`에서 이메일 인증/비밀번호 재설정 FE를 붙이던 중 실제로 눌러보니 항상
`500 (C901 EXTERNAL_SERVICE_ERROR)`로 실패했다. 처음엔 "`spring.mail.host`가
`application.yml`에 없어서"라고 판단해 base/`dev` 프로필에 `host`/`port`를 추가하고,
로컬용 가짜 SMTP(Mailpit)도 docker-compose에 붙였는데도 — `dev` 프로필로 띄운 채로도
**실제 `smtp.gmail.com`에 연결을 시도하다 인증 실패**하는 로그가 나왔다. 설정을
바꿔도 전혀 반영되지 않는 상황.

## ② 원인 분석

`GlobalConfig` 패키지에 `application.yml`과 무관하게 동작하는
`EmailConfig`(`src/main/java/.../global/config/EmailConfig.java`)가 있었다:

```java
@Bean
public JavaMailSender mailSender() {
    JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
    mailSender.setHost("smtp.gmail.com");   // 하드코딩
    mailSender.setPort(587);                // 하드코딩
    mailSender.setUsername(username);       // @Value("${spring.mail.username}") 만 주입
    mailSender.setPassword(password);
    ...
}
```

`JavaMailSender` Bean을 애플리케이션이 직접 정의하면 Spring Boot의
`MailSenderAutoConfiguration`(`spring.mail.*` 프로�티를 읽어 Bean을 만드는 쪽)은
조건부 자동설정 규칙에 따라 **아예 작동을 접는다.** 그 결과 `spring.mail.host`,
`port`, `properties.mail.smtp.*` 는 YAML 어디에 뭘 써도 완전히 무시되고, 오직
`username`/`password`만 `@Value`로 반영된 채 항상 `smtp.gmail.com:587`로 나간다.

- `dev` 프로필: `username`/`password`가 더미(`dev@example.com`/`dev`) → 실제 Gmail이
  "Username and Password not accepted"로 거절 → 항상 실패. Mailpit을 붙여도 애초에
  Mailpit 쪽으로 연결을 시도하지 않으니 무의미했음.
- `local`/`prod` 프로필: 실제 Gmail 계정을 `SPRING_MAIL_USERNAME`/`PASSWORD`로 넣으면
  우연히 동작은 하지만(host가 마침 Gmail로 하드코딩돼 있어서), 다른 SMTP 공급자로
  바꾸려면 YAML이 아니라 **이 Java 코드**를 고쳐야 하는 구조였음.
- `RedisConfig`가 오토컨피그된 `RedisConnectionFactory`를 우회하던 것(I-18)과 완전히
  같은 패턴 — "프레임워크가 해주는 걸 손으로 다시 만들면 오토컨피그가 주는 걸 전부 잃는다"는
  회고를 이메일에서도 반복한 셈.

## ③ 해결 방안 검토

| 옵션 | 장점 | 단점 | 채택 |
| --- | --- | --- | --- |
| A. `EmailConfig`를 고쳐서 `host`/`port`도 `@Value`로 주입 | 변경 최소 | 오토컨피그가 주는 커넥션 검증/헬스체크 등을 계속 못 씀. 여전히 손으로 관리 | |
| B. `EmailConfig` 삭제, `spring.mail.*` 오토컨피그에 위임 | I-18과 동일한 해법으로 일관성. `host`/`port`/`properties.*` 전부 YAML/프로필로 관리 가능해짐 | 없음 | ✅ |

## ④ 구현

- `EmailConfig.java` 삭제. `EmailService`는 그대로(`JavaMailSender` 주입 방식 변경 없음) —
  Bean을 어디서 만드는지만 바뀜.
- `application.yml`:
  - base: `spring.mail.host: ${SPRING_MAIL_HOST}`, `port: ${SPRING_MAIL_PORT:587}` 추가
    (기존엔 `username`/`password`만 있었음).
  - `dev` 프로필: `host`/`port` 기본값을 로컬 Mailpit(`localhost:1025`)으로, `auth`/
    `starttls.enable`을 `false`로(Mailpit은 인증·TLS 불필요).
- `docker-compose.yml`: `mailpit`(가짜 SMTP + 웹 UI `:8025`) 서비스 추가.
- `application-prod.yml.example`: `host`/`port` 기본값을 Gmail(`smtp.gmail.com:587`)로,
  앱 비밀번호 필요하다는 주석 추가.
- `application-local.yml.example`: `host`/`port` 플레이스홀더 추가 + Mailpit 재사용 안내.

## ⑤ 검증

Docker Desktop 로컬 Testcontainers 제약(S0에 기록된 기존 이슈)으로 `EmailFlowTest`는
CI에서 최종 확인하고, `dev`/`local` 프로필 실기동으로 3단 전부 직접 확인:

| 시나리오 | 수정 전 | 수정 후 |
| --- | --- | --- |
| `dev` (Mailpit) — 인증코드 발송 | `500 C901`, 로그엔 `smtp.gmail.com` 연결 시도 | `200`, Mailpit API로 실제 수신 확인(`GET :8025/api/v1/messages` → 1건) |
| `dev` — 발송된 코드로 `/user/email/verify` | (도달 불가) | `verified: true` |
| `local` (실제 Gmail 계정) — 인증코드 발송 | 해당 없음(이번에 처음 실제 계정 연동) | `200`, 에러 로그 없음, 실제 수신함에 도착 |

- `EmailFlowTest`(신규, GreenMail 사용): 회원가입 인증코드 발송+검증, 중복 이메일 거절
  시 메일 미발송 확인, 비밀번호 재설정 발송→재설정→새 비밀번호 로그인까지 — 이 경로를
  태우는 테스트가 이전엔 0개였음(`application-test.yml`의 `mail.port: 3025`는 GreenMail
  관례 포트인데 실제로는 연결된 적이 없었음).
- `./gradlew compileJava` 통과.

## ⑥ 회고

- "설정을 바꿔도 반영이 안 된다"는 신호는 대체로 그 설정을 읽는 코드가 아예 없거나,
  다른 코드가 먼저 가로채고 있다는 뜻이다. YAML만 계속 고치기 전에 `@Bean` 정의를
  먼저 그렙했어야 빨리 찾았을 문제.
- I-18(Redis)과 이번(Mail) 둘 다 "프레임워크 오토컨피그를 손으로 재구현"한 코드였다 —
  같은 패턴이 반복된다는 건 이 코드베이스 초기에 오토컨피그를 신뢰하지 않고 명시적으로
  다 구성하려는 습관이 있었다는 뜻으로 보인다. 커스텀 `@Bean`이 있으면 "이게 오토컨피그를
  대체하는 이유가 있는가"를 먼저 물어야 한다.
- 이번 건은 `newspin-web` FE 작업 도중 실제로 버튼을 눌러보다가 발견했다 — 코드 리뷰만으론
  못 잡았을 결함. 로컬에서 실제로 실행해보는 검증 단계가 없었으면 계속 묻혀 있었을 것.
