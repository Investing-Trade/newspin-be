package org.gp.newspinbe.domain.news.domain;

import java.time.LocalDateTime;

import org.gp.newspinbe.domain.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
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

    // S-2: 이 뉴스에 대한 사용자의 감성 판단과 그때 newspin-ai 가 준 정답. 판단 없이 학습만 하면 null.
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NewsSentiment userSentiment;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NewsSentiment aiSentiment;

    private LocalDateTime judgedAt;

    private UserNewsProgress(User user, NewsArticle newsArticle, LocalDateTime learnedAt) {
        this.user = user;
        this.newsArticle = newsArticle;
        this.learnedAt = learnedAt;
    }

    /** 감성 판단 결과까지 담아 생성 (S-2). */
    public static UserNewsProgress withJudgment(User user, NewsArticle newsArticle,
            NewsSentiment userSentiment, NewsSentiment aiSentiment) {
        UserNewsProgress progress = new UserNewsProgress(user, newsArticle, LocalDateTime.now());
        progress.recordJudgment(userSentiment, aiSentiment);
        return progress;
    }

    /** 이미 학습한 뉴스를 다시 판단한 경우 최신 판단으로 갱신. */
    public void recordJudgment(NewsSentiment userSentiment, NewsSentiment aiSentiment) {
        this.userSentiment = userSentiment;
        this.aiSentiment = aiSentiment;
        this.judgedAt = LocalDateTime.now();
        if (this.learnedAt == null) {
            this.learnedAt = this.judgedAt;
        }
    }

    public boolean isJudged() {
        return userSentiment != null && aiSentiment != null;
    }

    public boolean isCorrect() {
        return isJudged() && userSentiment == aiSentiment;
    }
}
