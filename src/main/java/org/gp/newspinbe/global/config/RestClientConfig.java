package org.gp.newspinbe.global.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Value("${gemini.api-key}")
    private String geminiApiKey;

    @Value("${gemini.model}")
    private String geminiModel;

    @Bean
    public RestClient geminiRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/models/" + geminiModel)
                .defaultHeader("Content-Type", "application/json")
                .requestFactory(factory)
                .build();
    }

    @Value("${ai-service.url:http://localhost:8000}")
    private String aiServiceUrl;

    @Bean
    public RestClient aiAnalysisRestClient() {
        return RestClient.builder()
               .baseUrl(aiServiceUrl)
                .defaultHeader("Content-Type", "application/json")
                // 추가: AI 서버가 JSON 응답을 반환하도록 Accept 헤더 추가
                // 이 헤더가 없으면 body가 null로 전송될 수 있음
                .defaultHeader("Accept", "application/json")
                .build();
    }

    public String getGeminiApiKey() {
        return geminiApiKey;
    }
}
