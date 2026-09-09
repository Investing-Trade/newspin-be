package org.gp.newspinbe.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Testcontainers(MySQL/Redis) + Flyway + 시드 적재가 걸린 통합 테스트.
 * 컨텍스트가 뜨는 것만으로도 "Flyway baseline 이 엔티티와 일치(ddl-auto: validate)"를 검증한다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}
