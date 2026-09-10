package org.gp.newspinbe.domain.ai.report.service;

import java.math.BigDecimal;
import java.util.List;

import org.gp.newspinbe.domain.ai.report.domain.InvestmentReport;
import org.gp.newspinbe.domain.ai.report.dto.response.InvestmentReportResponse;
import org.gp.newspinbe.domain.ai.report.repository.InvestmentReportRepository;
import org.gp.newspinbe.domain.simulation.domain.AssetHistory;
import org.gp.newspinbe.domain.simulation.domain.SimulationSession;
import org.gp.newspinbe.domain.simulation.domain.TradeType;
import org.gp.newspinbe.domain.simulation.repository.AssetHistoryRepository;
import org.gp.newspinbe.domain.simulation.repository.SimulationSessionRepository;
import org.gp.newspinbe.domain.simulation.repository.TradeRepository;
import org.gp.newspinbe.global.exception.CustomException;
import org.gp.newspinbe.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;

/**
 * 투자 리포트 조회 (I-11: AI 분석은 비동기).
 * 규칙 기반 요약은 즉시 계산하고, AI 분석 4개 섹션은 백그라운드({@link ReportGenerator})에서
 * 생성해 {@link InvestmentReport} 에 저장한다. status 로 진행 상태를 알린다.
 */
@Service
@RequiredArgsConstructor
public class InvestmentReportService {

    private final SimulationSessionRepository sessionRepository;
    private final TradeRepository tradeRepository;
    private final AssetHistoryRepository assetHistoryRepository;
    private final InvestmentReportRepository reportRepository;
    private final ReportGenerator reportGenerator;

    @Transactional
    public InvestmentReportResponse getReport(Long sessionId, Long userId) {
        SimulationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));
        if (!session.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN_ACCESS);
        }

        InvestmentReport report = reportRepository.findBySessionId(sessionId).orElse(null);
        boolean kickGeneration = false;

        if (report == null) {
            report = reportRepository.save(InvestmentReport.generating(sessionId));
            kickGeneration = true;
        } else if (report.getStatus() == org.gp.newspinbe.domain.ai.report.domain.ReportStatus.FAILED) {
            report.restartGeneration(); // GET 마다 실패한 리포트는 재시도
            kickGeneration = true;
        }

        if (kickGeneration) {
            runAfterCommit(() -> reportGenerator.generateAsync(sessionId));
        }

        return buildResponse(session, report);
    }

    private InvestmentReportResponse buildResponse(SimulationSession session, InvestmentReport report) {
        AssetHistory latest = assetHistoryRepository.findFirstBySessionOrderByRecordDateDesc(session).orElse(null);
        long totalTrades = tradeRepository.countBySession(session);
        long buyCount = tradeRepository.countBySessionAndTradeType(session, TradeType.BUY);
        long sellCount = tradeRepository.countBySessionAndTradeType(session, TradeType.SELL);

        BigDecimal finalAsset = latest != null ? latest.getTotalAsset() : session.getInitialCapital();
        Double profitRate = latest != null ? latest.getProfitRate() : 0.0;
        boolean ready = report.isReady();

        return InvestmentReportResponse.builder()
                .sessionId(session.getSessionId())
                .startDate(session.getStartDate())
                .endDate(session.getEndDate())
                .initialCapital(session.getInitialCapital())
                .finalAsset(finalAsset)
                .totalProfitRate(profitRate)
                .totalTradeCount(totalTrades)
                .buyCount(buyCount)
                .sellCount(sellCount)
                .status(report.getStatus().name())
                .overallAnalysis(ready ? report.getOverallAnalysis() : null)
                .newsResponseAnalysis(ready ? report.getNewsResponseAnalysis() : null)
                .riskManagementAnalysis(ready ? report.getRiskManagementAnalysis() : null)
                .improvementSuggestions(ready ? report.getImprovementSuggestions() : null)
                .generatedAt(report.getGeneratedAt())
                .build();
    }

    /** 트랜잭션 커밋 이후에 실행 — 커밋 전에 async 가 돌면 GENERATING 행을 못 볼 수 있으므로. */
    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
