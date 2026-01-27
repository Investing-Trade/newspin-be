package org.gp.newspinbe.domain.news.domain;

import java.time.LocalDate;

import org.gp.newspinbe.global.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsArticle extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long newsId;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(nullable = false)
	private LocalDate articleDate;

	private NewsArticle(String title, String content, LocalDate articleDate) {
		this.title = title;
		this.content = content;
		this.articleDate = articleDate;
	}

	public static NewsArticle createNewsArticle(String title, String content, LocalDate articleDate) {
		return new NewsArticle(title, content, articleDate);
	}
}
