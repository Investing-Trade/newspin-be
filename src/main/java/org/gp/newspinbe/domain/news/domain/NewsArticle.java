package org.gp.newspinbe.domain.news.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.gp.newspinbe.domain.event.domain.EventType;
import org.gp.newspinbe.domain.stock.domain.Stock;
import org.gp.newspinbe.global.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsArticle extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long newsId;

	@Column(nullable = false, length = 500)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(nullable = false)
	private LocalDate articleDate;

	@Column(length = 100)
	private String source;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private NewsSentiment sentiment;

	// 이벤트 타입 (null이면 일반 뉴스, 값이 있으면 이벤트 뉴스)
	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private EventType eventType;

	// AI 감성 분석 필드
	@Column
	private Double sentimentScore;

	@Column(columnDefinition = "TEXT")
	private String sentimentReason;

	// 관련 종목 (N:M 관계)
	@ManyToMany
	@JoinTable(name = "news_stock", joinColumns = @JoinColumn(name = "news_id"), inverseJoinColumns = @JoinColumn(name = "stock_id"))
	private List<Stock> relatedStocks = new ArrayList<>();

	private NewsArticle(String title, String content, LocalDate articleDate, String source) {
		this.title = title;
		this.content = content;
		this.articleDate = articleDate;
		this.source = source;
	}

	// 일반 뉴스 생성
	public static NewsArticle createNewsArticle(String title, String content, LocalDate articleDate, String source) {
		return new NewsArticle(title, content, articleDate, source);
	}

	// 감성 포함 일반 뉴스 생성
	public static NewsArticle createNewsWithSentiment(String title, String content, LocalDate articleDate,
			String source, NewsSentiment sentiment) {
		NewsArticle article = new NewsArticle(title, content, articleDate, source);
		article.sentiment = sentiment;
		return article;
	}

	// 이벤트 뉴스 생성 (이벤트 타입 포함)
	public static NewsArticle createEventNews(String title, String content, LocalDate articleDate,
			String source, NewsSentiment sentiment, EventType eventType) {
		NewsArticle article = new NewsArticle(title, content, articleDate, source);
		article.sentiment = sentiment;
		article.eventType = eventType;
		return article;
	}

	// 이벤트 뉴스인지 확인
	public boolean isEvent() {
		return this.eventType != null;
	}

	// 감성 분석 결과 설정
	public void updateSentiment(Double sentimentScore, String sentimentReason) {
		this.sentimentScore = sentimentScore;
		this.sentimentReason = sentimentReason;
	}

	// 관련 종목 추가
	public void addRelatedStock(Stock stock) {
		if (!this.relatedStocks.contains(stock)) {
			this.relatedStocks.add(stock);
		}
	}
}
