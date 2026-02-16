package org.gp.newspinbe.domain.news.dto.response;

import java.time.LocalDate;

import org.gp.newspinbe.domain.news.domain.NewsArticle;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NewsResponse {
    private Long newsId;
    private String title;
    private String content;
    private LocalDate articleDate;
    private String source;
    private org.gp.newspinbe.domain.news.domain.NewsSentiment sentiment;
    private boolean isEvent;

    public static NewsResponse from(NewsArticle newsArticle) {
        return NewsResponse.builder()
                .newsId(newsArticle.getNewsId())
                .title(newsArticle.getTitle())
                .content(newsArticle.getContent())
                .articleDate(newsArticle.getArticleDate())
                .source(newsArticle.getSource())
                .sentiment(newsArticle.getSentiment())
                .isEvent(false) // 일반 뉴스는 false
                .build();
    }
}
