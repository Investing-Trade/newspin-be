package org.gp.newspinbe.domain.ai.report.domain;

import org.gp.newspinbe.global.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 세션당 1건의 투자 리포트. AI 분석 4개 섹션은 백그라운드에서 채워진다 (I-11). */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "investment_report", uniqueConstraints = {
        @UniqueConstraint(columnNames = "session_id")
})
public class InvestmentReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reportId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    @Column(columnDefinition = "TEXT")
    private String overallAnalysis;

    @Column(columnDefinition = "TEXT")
    private String newsResponseAnalysis;

    @Column(columnDefinition = "TEXT")
    private String riskManagementAnalysis;

    @Column(columnDefinition = "TEXT")
    private String improvementSuggestions;

    @Column(length = 500)
    private String errorMessage;

    private java.time.LocalDateTime generatedAt;

    private InvestmentReport(Long sessionId) {
        this.sessionId = sessionId;
        this.status = ReportStatus.GENERATING;
    }

    public static InvestmentReport generating(Long sessionId) {
        return new InvestmentReport(sessionId);
    }

    public void restartGeneration() {
        this.status = ReportStatus.GENERATING;
        this.errorMessage = null;
    }

    public void markReady(String overall, String newsResponse, String risk, String improvement) {
        this.overallAnalysis = overall;
        this.newsResponseAnalysis = newsResponse;
        this.riskManagementAnalysis = risk;
        this.improvementSuggestions = improvement;
        this.status = ReportStatus.READY;
        this.errorMessage = null;
        this.generatedAt = java.time.LocalDateTime.now();
    }

    public void markFailed(String message) {
        this.status = ReportStatus.FAILED;
        this.errorMessage = message != null && message.length() > 500 ? message.substring(0, 500) : message;
    }

    public boolean isReady() {
        return status == ReportStatus.READY;
    }

    public boolean isGenerating() {
        return status == ReportStatus.GENERATING;
    }
}
