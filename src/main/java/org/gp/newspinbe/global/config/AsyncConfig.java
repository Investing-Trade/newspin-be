package org.gp.newspinbe.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * {@code @Async} 활성화 (I-11: 리포트 생성). 실행기는 Spring Boot 가 {@code spring.task.execution.*} 로
 * 오토컨피그한 {@code applicationTaskExecutor} 를 사용한다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
