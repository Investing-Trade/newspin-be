package org.gp.newspinbe.domain.news.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalAIResponse {
    private String request_id;
    private Long article_id;
    private String status;
    private SummaryInfo summary;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SummaryInfo {
        private Double positive_score;
        private Double negative_score;
        private Double neutral_score;
        private String overall_sentiment;
        private List<String> positive_keywords;
        private List<String> negative_keywords;
        private List<String> dominant_categories;
    }
}
