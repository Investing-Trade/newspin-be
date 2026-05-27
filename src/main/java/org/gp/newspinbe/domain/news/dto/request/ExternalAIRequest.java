package org.gp.newspinbe.domain.news.dto.request;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExternalAIRequest {
    private final String request_id;
    private final ArticleInfo article;
    private final AnalysisOptions options;

    @Getter
    @Builder
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
    public static class AnalysisOptions {
        private final Integer max_snippets;
        private final Boolean include_weak_snippets;
        private final Boolean include_raw_model_output;
    }
}
