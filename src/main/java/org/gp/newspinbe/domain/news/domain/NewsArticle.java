package org.gp.newspinbe.domain.news.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
	private String source; // 뉴스 출처 (예: "한국경제", "매일경제")

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private NewsSentiment sentiment; // 뉴스 감성 (호재/악재/중립)

	// AI 감성 분석 필드
	@Column
	private Double sentimentScore; // 감성 점수

	@Column(columnDefinition = "TEXT")
	private String sentimentReason; // 감성 분석 이유

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

	public static NewsArticle createNewsArticle(String title, String content, LocalDate articleDate, String source) {
		return new NewsArticle(title, content, articleDate, source);
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
