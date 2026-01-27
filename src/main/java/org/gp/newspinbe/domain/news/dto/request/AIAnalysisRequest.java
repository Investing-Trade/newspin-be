package org.gp.newspinbe.domain.news.dto.request;

import org.gp.newspinbe.domain.news.domain.NewsSentiment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AIAnalysisRequest {

    @NotNull(message = "호재/악재 판단은 필수입니다.")
    private NewsSentiment sentiment;

    @NotBlank(message = "판단 이유는 필수입니다.")
    private String reason;
}
