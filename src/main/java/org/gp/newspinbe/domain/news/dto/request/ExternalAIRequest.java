package org.gp.newspinbe.domain.news.dto.request;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized; // 추가: Jackson이 @Builder 클래스를 올바르게 직렬화하도록 하는 어노테이션

@Getter
@Builder
@Jacksonized // 추가: Jackson이 @Builder 클래스를 올바르게 직렬화하도록 함
public class ExternalAIRequest {
    private final String request_id;
    private final ArticleInfo article;
    private final AnalysisOptions options;

    @Getter
    @Builder
    @Jacksonized // 추가
    public static class ArticleInfo {
        private final Long article_id;
        private final String title;
        private final String content;
        private final String articleDate;
        private final String source;
        private final List<String> relatedStocks;
    }

    @Getter
    @Builder
    @Jacksonized // 추가
    public static class AnalysisOptions {
        private final Integer max_snippets;
        private final Boolean include_weak_snippets;
        private final Boolean include_raw_model_output;
    }
}