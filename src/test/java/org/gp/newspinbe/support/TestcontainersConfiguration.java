package org.gp.newspinbe.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트용 MySQL 컨테이너 (docker-compose 와 같은 이미지 태그).
 *
 * <p>Redis 는 {@code RedisConfig} 가 {@code @Value} 로 직접 커넥션 팩토리를 만들어
 * {@code @ServiceConnection} 을 우회하므로 컨테이너를 띄우지 않는다. 테스트는 Redis 를
 * 호출하지 않고, 값은 {@code application-test.yml} 에서 채운다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                .withDatabaseName("newspin")
                .withReuse(true);
    }
}
