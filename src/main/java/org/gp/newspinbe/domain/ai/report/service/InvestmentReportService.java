package org.gp.newspinbe.domain.ai.report.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.gp.newspinbe.domain.event.domain.EventStockImpact;
import org.gp.newspinbe.domain.event.repository.EventStockImpactRepository;
import org.gp.newspinbe.domain.news.domain.NewsArticle;
import org.gp.newspinbe.domain.news.repository.NewsArticleRepository;
import org.gp.newspinbe.domain.ai.report.dto.response.InvestmentReportResponse;
import org.gp.newspinbe.domain.simulation.domain.AssetHistory;
import org.gp.newspinbe.domain.simulation.domain.SimulationSession;
import org.gp.newspinbe.domain.simulation.domain.Trade;
import org.gp.newspinbe.domain.simulation.domain.TradeType;
import org.gp.newspinbe.domain.simulation.repository.AssetHistoryRepository;
import org.gp.newspinbe.domain.simulation.repository.SimulationSessionRepository;
import org.gp.newspinbe.domain.simulation.repository.TradeRepository;
import org.gp.newspinbe.global.exception.CustomException;
import org.gp.newspinbe.global.exception.ErrorCode;
import org.gp.newspinbe.global.service.GeminiService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class InvestmentReportService {

    private final SimulationSessionRepository sessionRepository;
    private final TradeRepository tradeRepository;
    private final AssetHistoryRepository assetHistoryRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final EventStockImpactRepository eventStockImpactRepository;
    private final GeminiService geminiService;

    public InvestmentReportResponse generateReport(Long sessionId, Long userId) {
        // 1. 세션 조회 및 권한 확인
        SimulationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

        if (!session.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
        }

        // 2. 규칙 기반 데이터 수집
        List<Trade> allTrades = tradeRepository.findBySessionOrderByCreatedAtAsc(session);
        List<AssetHistory> assetHistories = assetHistoryRepository
                .findBySessionOrderByRecordDateAsc(session);

        AssetHistory latestHistory = assetHistoryRepository
                .findFirstBySessionOrderByRecordDateDesc(session)
                .orElse(null);

        long buyCount = tradeRepository.countBySessionAndTradeType(session, TradeType.BUY);
        long sellCount = tradeRepository.countBySessionAndTradeType(session, TradeType.SELL);

        BigDecimal finalAsset = latestHistory != null
                ? latestHistory.getTotalAsset()
                : session.getInitialCapital();
        Double totalProfitRate = latestHistory != null
                ? latestHistory.getProfitRate()
                : 0.0;

        // 3. 이벤트 뉴스 및 영향도 데이터 수집
        List<NewsArticle> eventNews = newsArticleRepository
                .findByArticleDateBetweenAndEventTypeIsNotNull(
                        session.getStartDate(), session.getEndDate());

        List<EventStockImpact> allImpacts = eventNews.isEmpty()
                ? List.of()
                : eventStockImpactRepository.findByNewsArticles(eventNews);

        // 4. Gemini 프롬프트 생성 및 AI 분석
        String prompt = buildPrompt(session, allTrades, assetHistories, eventNews, allImpacts);
        String aiResponse = geminiService.generateContent(prompt);

        // 5. AI 응답 파싱
        String overallAnalysis = extractSection(aiResponse, "종합 분석");
        String newsResponseAnalysis = extractSection(aiResponse, "뉴스 대응 분석");
        String riskManagementAnalysis = extractSection(aiResponse, "리스크 관리 분석");
        String improvementSuggestions = extractSection(aiResponse, "개선 제안");

        // 6. 응답 구성
        return InvestmentReportResponse.builder()
                .sessionId(sessionId)
                .startDate(session.getStartDate())
                .endDate(session.getEndDate())
                .initialCapital(session.getInitialCapital())
                .finalAsset(finalAsset)
                .totalProfitRate(totalProfitRate)
                .totalTradeCount(allTrades.size())
                .buyCount(buyCount)
                .sellCount(sellCount)
                .overallAnalysis(overallAnalysis)
                .newsResponseAnalysis(newsResponseAnalysis)
                .riskManagementAnalysis(riskManagementAnalysis)
                .improvementSuggestions(improvementSuggestions)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Gemini에 전달할 프롬프트 생성
     * 규칙 기반으로 정리된 데이터를 구조화하여 전달
     */
    private String buildPrompt(SimulationSession session, List<Trade> trades,
            List<AssetHistory> assetHistories, List<NewsArticle> eventNews,
            List<EventStockImpact> allImpacts) {

        StringBuilder sb = new StringBuilder();

        sb.append("당신은 투자 교육 전문가입니다. 아래 모의투자 시뮬레이션 데이터를 분석하여 사용자에게 투자 피드백을 제공해주세요.\n\n");

        // 시뮬레이션 기본 정보
        sb.append("== 시뮬레이션 기본 정보 ==\n");
        sb.append(String.format("- 기간: %s ~ %s\n", session.getStartDate(), session.getEndDate()));
        sb.append(String.format("- 초기 자본: %s원\n", session.getInitialCapital()));
        sb.append(String.format("- 현재 자본(현금): %s원\n", session.getCurrentCapital()));
        sb.append("\n");

        // 자산 변동 추이
        sb.append("== 일별 자산 변동 ==\n");
        for (AssetHistory history : assetHistories) {
            sb.append(String.format("- %s: 총자산 %s원, 수익률 %.2f%%\n",
                    history.getRecordDate(), history.getTotalAsset(), history.getProfitRate()));
        }
        sb.append("\n");

        // 매매 기록
        sb.append("== 사용자 매매 기록 ==\n");
        if (trades.isEmpty()) {
            sb.append("- 매매 기록 없음\n");
        } else {
            for (Trade trade : trades) {
                sb.append(String.format("- %s: %s %s %d주 (주당 %s원, 총 %s원)\n",
                        trade.getTradeDate(),
                        trade.getStock().getStockName(),
                        trade.isBuy() ? "매수" : "매도",
                        trade.getQuantity(),
                        trade.getPrice(),
                        trade.getTotalAmount()));
            }
        }
        sb.append("\n");

        // 이벤트 뉴스 + 영향도 (정답지)
        sb.append("== 이벤트 뉴스 및 실제 영향도 (사용자는 몰랐던 정보) ==\n");
        if (eventNews.isEmpty()) {
            sb.append("- 이벤트 뉴스 없음\n");
        } else {
            for (NewsArticle news : eventNews) {
                sb.append(String.format("\n[이벤트] %s (%s)\n", news.getTitle(), news.getArticleDate()));
                sb.append(String.format("  뉴스 내용: %s\n", news.getContent()));
                sb.append(String.format("  이벤트 타입: %s\n", news.getEventType()));

                // 해당 이벤트의 종목별 영향도
                List<EventStockImpact> impacts = allImpacts.stream()
                        .filter(impact -> impact.getNewsArticle().getNewsId().equals(news.getNewsId()))
                        .collect(Collectors.toList());

                if (!impacts.isEmpty()) {
                    sb.append("  종목별 실제 영향도:\n");
                    for (EventStockImpact impact : impacts) {
                        sb.append(String.format("    - %s: %+.1f%% (%s)\n",
                                impact.getStock().getStockName(),
                                impact.getImpactRate(),
                                impact.getImpactReason()));
                    }
                }

                // 사용자가 이 이벤트 전후에 한 매매
                LocalDate eventDate = news.getArticleDate();
                List<Trade> nearbyTrades = trades.stream()
                        .filter(t -> !t.getTradeDate().isBefore(eventDate.minusDays(1))
                                && !t.getTradeDate().isAfter(eventDate.plusDays(2)))
                        .collect(Collectors.toList());

                sb.append("  사용자의 이벤트 전후 매매:\n");
                if (nearbyTrades.isEmpty()) {
                    sb.append("    - 해당 기간 매매 없음 (무반응)\n");
                } else {
                    for (Trade t : nearbyTrades) {
                        sb.append(String.format("    - %s: %s %s %d주\n",
                                t.getTradeDate(),
                                t.getStock().getStockName(),
                                t.isBuy() ? "매수" : "매도",
                                t.getQuantity()));
                    }
                }
            }
        }
        sb.append("\n");

        // 응답 형식 지시
        sb.append("== 분석 요청 ==\n");
        sb.append("위 데이터를 바탕으로 다음 4개 섹션으로 나누어 한국어로 분석해주세요.\n");
        sb.append("각 섹션은 '## 섹션명' 형태로 시작해주세요.\n\n");
        sb.append("## 종합 분석\n");
        sb.append("전체적인 투자 성과와 전략을 평가해주세요.\n\n");
        sb.append("## 뉴스 대응 분석\n");
        sb.append("이벤트 뉴스에 대한 사용자의 대응을 평가해주세요. 뉴스를 잘 읽고 수혜주를 파악했는지, 타이밍은 적절했는지 분석해주세요.\n\n");
        sb.append("## 리스크 관리 분석\n");
        sb.append("포트폴리오 분산, 현금 비중, 손절/익절 타이밍 등을 평가해주세요.\n\n");
        sb.append("## 개선 제안\n");
        sb.append("구체적인 개선 방안을 3~5개 제시해주세요.\n");

        return sb.toString();
    }

    /**
     * AI 응답에서 특정 섹션 추출
     * "## 섹션명" ~ 다음 "##" 사이의 내용을 추출
     */
    private String extractSection(String response, String sectionName) {
        String marker = "## " + sectionName;
        int startIndex = response.indexOf(marker);
        if (startIndex == -1) {
            return "분석 결과를 파싱할 수 없습니다.";
        }

        startIndex += marker.length();
        int endIndex = response.indexOf("## ", startIndex);
        if (endIndex == -1) {
            endIndex = response.length();
        }

        return response.substring(startIndex, endIndex).trim();
    }
}
