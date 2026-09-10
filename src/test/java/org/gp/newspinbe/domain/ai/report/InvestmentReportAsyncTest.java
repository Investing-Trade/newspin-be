package org.gp.newspinbe.domain.ai.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.gp.newspinbe.domain.ai.report.domain.InvestmentReport;
import org.gp.newspinbe.domain.ai.report.domain.ReportStatus;
import org.gp.newspinbe.domain.ai.report.dto.response.InvestmentReportResponse;
import org.gp.newspinbe.domain.ai.report.repository.InvestmentReportRepository;
import org.gp.newspinbe.domain.ai.report.service.InvestmentReportService;
import org.gp.newspinbe.domain.ai.report.service.ReportGenerator;
import org.gp.newspinbe.domain.simulation.domain.SimulationSession;
import org.gp.newspinbe.domain.simulation.repository.SimulationSessionRepository;
import org.gp.newspinbe.domain.user.domain.User;
import org.gp.newspinbe.domain.user.repository.UserRepository;
import org.gp.newspinbe.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * I-11. 리포트 AI 분석은 비동기.
 * <ul>
 *   <li>첫 조회는 규칙 기반 요약만 즉시 반환하고 {@code status=GENERATING}, AI 섹션은 비어 있다.</li>
 *   <li>백그라운드 {@link ReportGenerator} 는 Gemini 실패(더미 키)에도 500 없이 4개 섹션을
 *       fallback 으로 채워 {@code READY} 로 만든다 — 기존 C-4 계약 유지.</li>
 * </ul>
 */
@IntegrationTest
class InvestmentReportAsyncTest {

    @Autowired InvestmentReportService reportService;
    @Autowired ReportGenerator reportGenerator;
    @Autowired InvestmentReportRepository reportRepository;
    @Autowired UserRepository userRepository;
    @Autowired SimulationSessionRepository sessionRepository;

    private SimulationSession newSession() {
        User user = userRepository.save(User.create("report-" + System.nanoTime() + "@t.com", "x"));
        return sessionRepository.save(SimulationSession.createSession(
                user, new BigDecimal("10000000"),
                LocalDate.parse("2020-02-03"), LocalDate.parse("2020-02-14")));
    }

    @Test
    void 첫_조회는_GENERATING_상태로_즉시_반환된다() {
        SimulationSession session = newSession();

        InvestmentReportResponse response = reportService.getReport(
                session.getSessionId(), session.getUser().getUserId());

        assertThat(response.getStatus()).isEqualTo(ReportStatus.GENERATING.name());
        assertThat(response.getOverallAnalysis()).isNull();
        assertThat(response.getNewsResponseAnalysis()).isNull();
        // 규칙 기반 요약은 채워져 있다
        assertThat(response.getInitialCapital()).isEqualByComparingTo("10000000");
        // GENERATING 행이 생성됐다
        assertThat(reportRepository.findBySessionId(session.getSessionId())).isPresent();
    }

    @Test
    void 생성_작업은_Gemini_실패시에도_fallback_섹션으로_READY_처리한다() {
        SimulationSession session = newSession();
        reportRepository.save(InvestmentReport.generating(session.getSessionId()));

        reportGenerator.generate(session.getSessionId());

        InvestmentReport report = reportRepository.findBySessionId(session.getSessionId()).orElseThrow();
        assertThat(report.getStatus()).isEqualTo(ReportStatus.READY);
        assertThat(report.getOverallAnalysis()).isNotBlank();
        assertThat(report.getOverallAnalysis()).doesNotContain("파싱할 수 없습니다");
        assertThat(report.getNewsResponseAnalysis()).isNotBlank();
        assertThat(report.getRiskManagementAnalysis()).isNotBlank();
        assertThat(report.getImprovementSuggestions()).isNotBlank();
    }
}
