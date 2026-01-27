package org.gp.newspinbe.domain.news.domain;

import java.time.LocalDateTime;

import org.gp.newspinbe.domain.user.domain.User;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = { "user_id", "news_id" })
})
public class UserNewsProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "news_id", nullable = false)
    private NewsArticle newsArticle;

    private LocalDateTime learnedAt;

    private UserNewsProgress(User user, NewsArticle newsArticle, LocalDateTime learnedAt) {
        this.user = user;
        this.newsArticle = newsArticle;
        this.learnedAt = learnedAt;
    }

    public static UserNewsProgress createUserNewsProgress(User user, NewsArticle newsArticle) {
        return new UserNewsProgress(user, newsArticle, LocalDateTime.now());
    }
}
