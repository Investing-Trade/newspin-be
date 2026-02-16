package org.gp.newspinbe.domain.news.dto.response;

import java.time.LocalDate;

import org.gp.newspinbe.domain.event.domain.EventType;
import org.gp.newspinbe.domain.news.domain.NewsArticle;
import org.gp.newspinbe.domain.news.domain.NewsSentiment;

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
    private NewsSentiment sentiment;
    private EventType eventType; // null이면 일반 뉴스, 값이 있으면 이벤트 뉴스

    public static NewsResponse from(NewsArticle newsArticle) {
        return NewsResponse.builder()
                .newsId(newsArticle.getNewsId())
                .title(newsArticle.getTitle())
                .content(newsArticle.getContent())
                .articleDate(newsArticle.getArticleDate())
                .source(newsArticle.getSource())
                .sentiment(newsArticle.getSentiment())
                .eventType(newsArticle.getEventType())
                .build();
    }
}
