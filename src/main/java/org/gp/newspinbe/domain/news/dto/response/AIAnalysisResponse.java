package org.gp.newspinbe.domain.news.dto.response;

import org.gp.newspinbe.domain.news.domain.NewsSentiment;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AIAnalysisResponse {
    private NewsSentiment aiSentiment;
    private String aiFeedback;
    private boolean isCorrect;
}
