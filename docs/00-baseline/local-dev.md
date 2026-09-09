# 로컬 실행

## 1. 인프라

```bash
docker compose up -d          # mysql:8.4 (3307), redis:7.4 (6380)
```

## 2. 앱

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

`dev` 프로필은 위 컨테이너를 바라보고, Flyway 마이그레이션 후 빈 DB면 시드를 1회 적재한다.
개인 설정은 `application-local.yml` (`.example` 복사, gitignore됨) 또는 환경변수로 오버라이드.

- Swagger: `http://localhost:8080/swagger-ui.html`
- Actuator: `http://localhost:8080/actuator/health`, `/actuator/prometheus`

## 3. 테스트

```bash
./gradlew test
```

통합 테스트(`@IntegrationTest`)는 Testcontainers 로 MySQL/Redis 를 띄운다 (Docker 필요).

**Windows + 최신 Docker Desktop:** named pipe 로 붙으면 docker-java 가 엔진 API(1.53) 와
버전 협상을 못 해 `Could not find a valid Docker environment` (HTTP 400) 가 난다. 우회:

1. Docker Desktop → Settings → General → **"Expose daemon on tcp://localhost:2375 without TLS"** 체크
2. `export DOCKER_HOST=tcp://localhost:2375` 후 `./gradlew test`

또는 WSL2 에서 실행. **GitHub Actions(ubuntu) 는 unix 소켓이라 우회 불필요** — 통합 테스트의
1차 검증처는 CI 다. 스키마/시드 검증은 `bootRun --spring.profiles.active=dev` 로도 확인된다.

## 4. 시드 데이터 재생성

`tools/seed/README.md` 참고.
