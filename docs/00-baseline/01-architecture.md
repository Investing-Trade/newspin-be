# 착수 시점 아키텍처 스냅샷 (2026-09-09)

개선 작업 시작 전 `newspin-be`의 상태를 코드 기준으로 고정 기록한다. 이후 "Before"의 기준점.

## 런타임 구성

```
[newspin-fe : React/Vite, localhost:5173]
        │  REST + JWT(Bearer)
        ▼
[newspin-be : Spring Boot 4.0.1, Java 25]
   ├── MySQL         — JPA/Hibernate, ddl-auto: update
   ├── Redis         — JWT refresh token 저장 / 이메일 인증코드 저장 (그 외 캐시 용도 없음)
   ├── Gemini API    — GeminiService, RestClient 기반 직접 호출 (google 공식 SDK 아님)
   └── newspin-ai    — java.net.http.HttpClient 를 매 요청 new 생성해서 호출 (connect 10s / read 30s)
                          │
                          ▼
                  [newspin-ai : FastAPI, Python 3.11, uvicorn workers=1]
                     규칙기반 스니펫 추출 → Gemini(structured JSON) 또는 로컬 KoELECTRA
```

- Gemini 연동 구현이 BE와 AI에 **두 벌** 존재하고 방식이 다르다. BE는 방어 로직이 거의 없다.
- 빌드 타깃은 Java 25(`build.gradle` 툴체인)인데 `Dockerfile`은 `amazoncorretto:21` → **불일치**.

## 도메인 패키지

`org.gp.newspinbe.domain.*` — `user`, `news`, `event`, `stock`, `simulation`, `ai.report`
`org.gp.newspinbe.global.*` — `config`, `exception`, `security`, `service(GeminiService)`, `common`, `util`

계층 네이밍이 도메인마다 다르다: `application` vs `service`, `presentation` vs `controller`.

## 엔드포인트 인벤토리

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | `/user/sign-up`, `/user/sign-in`, `/user/refresh`, `/user/logout` | 인증 |
| POST | `/user/email/send-verification`, `/user/email/verify` | 이메일 인증 |
| POST | `/user/password/send-reset-code`, `/user/password/reset` | 비밀번호 재설정 |
| GET | `/user/me` | 내 정보 |
| DELETE | `/user` | `hasRole("ADMIN")` — 사실상 도달 불가 (아래 이슈 참조) |
| GET | `/news/random` | 미학습 뉴스 1건 랜덤 |
| POST | `/news/{newsId}/analyze` | 감정 판단 제출 → AI 분석 + 튜터 피드백 |
| POST | `/simulation/sessions` | 세션 생성 |
| POST | `/simulation/sessions/{id}/next-day` | 다음 거래일로 진행 (DB 저장) |
| GET | `/simulation/sessions/{id}/daily-data` | 현재일 데이터 (저장 없음) |
| GET | `/simulation/sessions/{id}/portfolio` | 포트폴리오 개요 |
| GET | `/simulation/sessions`, `/simulation/sessions/{id}` | 세션 목록/상세 |
| DELETE | `/simulation/sessions/{id}` | 세션 포기(ABANDONED) |
| PUT | `/simulation/sessions/{id}/complete` | 세션 종료 |
| POST | `/simulation/sessions/{id}/trades` | 매수/매도 |
| GET | `/simulation/sessions/{id}/trades` | 거래 내역 |
| GET | `/simulation/sessions/{id}/report` | 투자 리포트 (Gemini 1회 호출) |
| GET | `/stocks/{code}/price-range`, `/stocks/price-range` | 주가 시계열 — ⚠️ `date` 기준 **이후 5영업일**까지 반환 (lookahead, C-5) |

## 핵심 플로우

### ① 뉴스 감정 판단 학습 — `AIService.analyzeUserJudgment`
1. `@Transactional` 진입 직후 **하드코딩된 더미 테스트 HTTP 호출** 실행 (매 요청마다, 결과는 로그만)
2. 뉴스 본문 2000자 컷 → `newspin-ai POST /api/v1/analyze` 호출
3. AI 응답의 `overall_sentiment` vs 유저 판단 비교 → 정오 판정
4. `GeminiService.generateContent`로 튜터 피드백 생성 (파싱 방어 없음)
5. `markNewsAsLearned`

전 과정이 하나의 `@Transactional(쓰기)` 안에서 실행 → 외부 HTTP(최대 30s + Gemini)가 트랜잭션/DB 커넥션을 물고 있음.

### ② 투자 리포트 — `InvestmentReportService.generateReport`
1. 세션의 Trade / AssetHistory 전량 조회
2. 이벤트 뉴스 + `EventStockImpact`(비공개 정답 데이터) 조회
3. 프롬프트를 `StringBuilder`로 선형 누적 (세션 길수록 토큰 증가)
4. Gemini 1회 호출 → `## 섹션명` 문자열 `indexOf`/`substring` 파싱
5. 파싱 실패 시 `"분석 결과를 파싱할 수 없습니다."` 문자열을 그대로 응답에 넣음

## 인증/보안 현황

- `SecurityConfig`: STATELESS, CSRF/폼로그인/로그아웃 비활성, JWT 필터 2개
- CORS origin 하드코딩: `localhost:5173`, `127.0.0.1:5173` (프로필 분리 없음)
- `CustomUserDetails.getAuthorities()` → `"USER"` (접두사 `ROLE_` 없음) → `hasRole("ADMIN")`/`hasRole("USER")` 항상 실패
- `User` 엔티티에 권한 필드 없음
- Redis refresh token 키 = `email` 단독 → 멀티 디바이스 로그인 시 마지막 로그인이 이전 토큰 덮어씀
- `application.yml`: `ddl-auto: update`, `org.hibernate.sql: debug` 상시 on
- JWT: access 24h / refresh 3d
- jjwt 0.11.5 (`parserBuilder`, `setSigningKey` 등 deprecated API 사용)

## 영속성 현황

- `open-in-view: false`, `default_batch_fetch_size: 100` 설정됨
- `NewsArticle.relatedStocks` = `@ManyToMany` (조인테이블 `news_stock`), fetch 전략 기본(LAZY)
- 리포지토리에 사용처 없는 분석용 쿼리 다수 (`findMaxProfitRateBySession`, `countBySectorForSession` 등 — 데드코드 후보)
- `StockPrice`: `open/close/high/low/volume` 중 로직은 `closePrice`만 사용
- `AssetHistory`: `(session_id, record_date)` 유니크. `profitRate`를 저장 시점에 계산해 컬럼 저장(비정규화)

## 테스트/빌드/배포 현황

- 테스트: `NewspinBeApplicationTests` (컨텍스트 로드) **1개뿐**. 실질 커버리지 0.
- 빌드: Gradle 9.2.1 래퍼. 로컬에 JDK 25 없음 → 툴체인 자동 프로비저닝 미설정 상태였음.
- `.gitignore`가 `application-local.yml`, `application-prod.yml`, `src/main/resources/data/`, `global/config/DataLoader.java`, `/docs` 를 제외 → **시드 데이터 적재 코드가 git에 없음**. "데이터 적재 완료" 보고와 GitHub 상태 불일치의 원인.
- CI 없음.
