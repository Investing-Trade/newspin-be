package org.gp.newspinbe.domain.ai.report.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InvestmentReportResponse {

    private Long sessionId;
    private LocalDate startDate;
    private LocalDate endDate;

    // 투자 성과 요약
    private BigDecimal initialCapital; // 초기 자본
    private BigDecimal finalAsset; // 최종 자산
    private Double totalProfitRate; // 총 수익률 (%)
    private long totalTradeCount; // 총 거래 횟수
    private long buyCount; // 매수 횟수
    private long sellCount; // 매도 횟수

    // AI 분석 결과
    private String overallAnalysis; // 종합 분석
    private String newsResponseAnalysis; // 뉴스 대응 분석
    private String riskManagementAnalysis; // 리스크 관리 분석
    private String improvementSuggestions; // 개선 제안

    private LocalDateTime generatedAt; // 보고서 생성 시각
}
