package org.gp.newspinbe.domain.ai.report.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.gp.newspinbe.domain.ai.report.domain.InvestmentReport;
import org.gp.newspinbe.domain.ai.report.repository.InvestmentReportRepository;
import org.gp.newspinbe.domain.event.domain.EventStockImpact;
import org.gp.newspinbe.domain.event.repository.EventStockImpactRepository;
import org.gp.newspinbe.domain.news.domain.NewsArticle;
import org.gp.newspinbe.domain.news.repository.NewsArticleRepository;
import org.gp.newspinbe.domain.simulation.domain.AssetHistory;
import org.gp.newspinbe.domain.simulation.domain.SimulationSession;
import org.gp.newspinbe.domain.simulation.domain.Trade;
import org.gp.newspinbe.domain.simulation.repository.AssetHistoryRepository;
import org.gp.newspinbe.domain.simulation.repository.SimulationSessionRepository;
import org.gp.newspinbe.domain.simulation.repository.TradeRepository;
import org.gp.newspinbe.global.service.GeminiService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 투자 리포트 AI 분석을 백그라운드에서 생성한다 (I-11). 결과는 {@link InvestmentReport} 행에 저장.
 * 호출 전에 {@code GENERATING} 상태의 행이 이미 존재해야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportGenerator {

    private final InvestmentReportRepository reportRepository;
    private final SimulationSessionRepository sessionRepository;
    private final TradeRepository tradeRepository;
    private final AssetHistoryRepository assetHistoryRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final EventStockImpactRepository eventStockImpactRepository;
    private final GeminiService geminiService;
    /** 자기 자신 프록시 — {@code generateAsync} 에서 {@code generate} 의 @Transactional 경계를 살리기 위해. */
    private final ObjectProvider<ReportGenerator> self;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                    "overallAnalysis", Map.of("type", "STRING"),
                    "newsResponseAnalysis", Map.of("type", "STRING"),
                    "riskManagementAnalysis", Map.of("type", "STRING"),
                    "improvementSuggestions", Map.of("type", "STRING")),
            "required", List.of("overallAnalysis", "newsResponseAnalysis",
                    "riskManagementAnalysis", "improvementSuggestions"));

    /** {@link InvestmentReportService} 가 커밋 이후 호출. 별도 스레드에서 {@link #generate} 실행. */
    @Async
    public void generateAsync(Long sessionId) {
        try {
            self.getObject().generate(sessionId);
        } catch (Exception e) {
            log.error("리포트 비동기 생성 실패 - sessionId {}", sessionId, e);
        }
    }

    @Transactional
    public void generate(Long sessionId) {
        InvestmentReport report = reportRepository.findBySessionId(sessionId).orElse(null);
        if (report == null || !report.isGenerating()) {
            return; // 이미 처리됐거나 행이 없음
        }
        try {
            SimulationSession session = sessionRepository.findById(sessionId).orElseThrow();
            ReportSections s = analyze(buildPrompt(session));
            report.markReady(s.overallAnalysis(), s.newsResponseAnalysis(),
                    s.riskManagementAnalysis(), s.improvementSuggestions());
        } catch (Exception e) {
            log.error("리포트 생성 실패 - sessionId {}: {}", sessionId, e.toString());
            report.markFailed(e.getMessage());
        }
    }

    private ReportSections analyze(String prompt) {
        String raw = geminiService.generateJson(prompt, RESPONSE_SCHEMA);
        try {
            ReportSections parsed = objectMapper.readValue(raw, ReportSections.class);
            if (parsed.overallAnalysis() == null) {
                throw new IllegalStateException("필수 필드 누락");
            }
            return parsed;
        } catch (Exception e) {
            log.warn("리포트 JSON 파싱 실패, fallback 사용: {}", e.getMessage());
            String msg = (raw != null && !raw.isBlank()) ? raw : "분석 결과를 생성하지 못했습니다.";
            return new ReportSections(msg, msg, msg, msg);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ReportSections(
            String overallAnalysis,
            String newsResponseAnalysis,
            String riskManagementAnalysis,
            String improvementSuggestions) {
    }

    private String buildPrompt(SimulationSession session) {
        List<Trade> trades = tradeRepository.findBySessionOrderByCreatedAtAsc(session);
        List<AssetHistory> assetHistories = assetHistoryRepository.findBySessionOrderByRecordDateAsc(session);
        List<NewsArticle> eventNews = newsArticleRepository.findByArticleDateBetweenAndEventTypeIsNotNull(
                session.getStartDate(), session.getEndDate());
        List<EventStockImpact> allImpacts = eventNews.isEmpty()
                ? List.of()
                : eventStockImpactRepository.findByNewsArticles(eventNews);

        StringBuilder sb = new StringBuilder();
        sb.append("당신은 투자 교육 전문가입니다. 아래 모의투자 시뮬레이션 데이터를 분석하여 사용자에게 투자 피드백을 제공해주세요.\n\n");

        sb.append("== 시뮬레이션 기본 정보 ==\n");
        sb.append(String.format("- 기간: %s ~ %s\n", session.getStartDate(), session.getEndDate()));
        sb.append(String.format("- 초기 자본: %s원\n", session.getInitialCapital()));
        sb.append(String.format("- 현재 자본(현금): %s원\n\n", session.getCurrentCapital()));

        sb.append("== 일별 자산 변동 ==\n");
        for (AssetHistory history : assetHistories) {
            sb.append(String.format("- %s: 총자산 %s원, 수익률 %.2f%%\n",
                    history.getRecordDate(), history.getTotalAsset(), history.getProfitRate()));
        }
        sb.append("\n");

        sb.append("== 사용자 매매 기록 ==\n");
        if (trades.isEmpty()) {
            sb.append("- 매매 기록 없음\n");
        } else {
            for (Trade trade : trades) {
                sb.append(String.format("- %s: %s %s %d주 (주당 %s원, 총 %s원)\n",
                        trade.getTradeDate(), trade.getStock().getStockName(),
                        trade.isBuy() ? "매수" : "매도", trade.getQuantity(),
                        trade.getPrice(), trade.getTotalAmount()));
            }
        }
        sb.append("\n");

        sb.append("== 이벤트 뉴스 및 실제 영향도 (사용자는 몰랐던 정보) ==\n");
        if (eventNews.isEmpty()) {
            sb.append("- 이벤트 뉴스 없음\n");
        } else {
            for (NewsArticle news : eventNews) {
                sb.append(String.format("\n[이벤트] %s (%s)\n", news.getTitle(), news.getArticleDate()));
                sb.append(String.format("  뉴스 내용: %s\n", news.getContent()));
                sb.append(String.format("  이벤트 타입: %s\n", news.getEventType()));

                List<EventStockImpact> impacts = allImpacts.stream()
                        .filter(i -> i.getNewsArticle().getNewsId().equals(news.getNewsId()))
                        .collect(Collectors.toList());
                if (!impacts.isEmpty()) {
                    sb.append("  종목별 실제 영향도:\n");
                    for (EventStockImpact impact : impacts) {
                        sb.append(String.format("    - %s: %+.1f%% (%s)\n",
                                impact.getStock().getStockName(),
                                impact.getImpactRate(), impact.getImpactReason()));
                    }
                }

                LocalDate eventDate = news.getArticleDate();
                List<Trade> nearby = trades.stream()
                        .filter(t -> !t.getTradeDate().isBefore(eventDate.minusDays(1))
                                && !t.getTradeDate().isAfter(eventDate.plusDays(2)))
                        .collect(Collectors.toList());
                sb.append("  사용자의 이벤트 전후 매매:\n");
                if (nearby.isEmpty()) {
                    sb.append("    - 해당 기간 매매 없음 (무반응)\n");
                } else {
                    for (Trade t : nearby) {
                        sb.append(String.format("    - %s: %s %s %d주\n",
                                t.getTradeDate(), t.getStock().getStockName(),
                                t.isBuy() ? "매수" : "매도", t.getQuantity()));
                    }
                }
            }
        }
        sb.append("\n");

        sb.append("== 분석 요청 ==\n");
        sb.append("위 데이터를 바탕으로 아래 4개 항목을 한국어로 분석해 JSON 객체로만 응답하세요.\n");
        sb.append("- overallAnalysis: 전체적인 투자 성과와 전략 평가\n");
        sb.append("- newsResponseAnalysis: 이벤트 뉴스 대응 평가 (수혜주 파악, 타이밍)\n");
        sb.append("- riskManagementAnalysis: 포트폴리오 분산, 현금 비중, 손절/익절 타이밍 평가\n");
        sb.append("- improvementSuggestions: 구체적 개선 방안 3~5개 (한 문자열, 줄바꿈으로 구분)\n");
        return sb.toString();
    }
}
