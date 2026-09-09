package org.gp.newspinbe.global.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.gp.newspinbe.global.service.GeminiService.GeminiBlockedException;
import org.gp.newspinbe.global.service.GeminiService.GeminiUnavailableException;
import org.junit.jupiter.api.Test;

/**
 * C-4. 캐스팅 체인 파싱을 방어적으로 교체 — 안전필터 차단/형식 이상에서 NPE 대신 명시적 예외.
 */
class GeminiResponseParseTest {

    @Test
    void 정상_응답에서_텍스트를_뽑는다() {
        Map<String, Object> response = Map.of("candidates", List.of(
                Map.of("content", Map.of("parts", List.of(Map.of("text", "안녕"), Map.of("text", "하세요"))),
                        "finishReason", "STOP")));

        assertThat(GeminiService.parseText(response)).isEqualTo("안녕하세요");
    }

    @Test
    void 안전필터로_parts가_비면_Blocked() {
        Map<String, Object> response = Map.of("candidates", List.of(
                Map.of("content", Map.of(), "finishReason", "SAFETY")));

        assertThatThrownBy(() -> GeminiService.parseText(response))
                .isInstanceOf(GeminiBlockedException.class);
    }

    @Test
    void content가_아예_없으면_Blocked_또는_Unavailable() {
        Map<String, Object> response = Map.of("candidates", List.of(Map.of("finishReason", "RECITATION")));

        assertThatThrownBy(() -> GeminiService.parseText(response))
                .isInstanceOf(GeminiBlockedException.class);
    }

    @Test
    void candidates가_없으면_Unavailable() {
        assertThatThrownBy(() -> GeminiService.parseText(Map.of()))
                .isInstanceOf(GeminiUnavailableException.class);
        assertThatThrownBy(() -> GeminiService.parseText(null))
                .isInstanceOf(GeminiUnavailableException.class);
    }

    @Test
    void 프롬프트가_차단되면_Blocked() {
        Map<String, Object> response = Map.of("promptFeedback", Map.of("blockReason", "SAFETY"));

        assertThatThrownBy(() -> GeminiService.parseText(response))
                .isInstanceOf(GeminiBlockedException.class);
    }
}
