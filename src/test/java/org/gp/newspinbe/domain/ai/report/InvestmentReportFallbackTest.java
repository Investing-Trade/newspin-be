package org.gp.newspinbe.domain.ai.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.gp.newspinbe.domain.ai.report.dto.response.InvestmentReportResponse;
import org.gp.newspinbe.domain.ai.report.service.InvestmentReportService;
import org.gp.newspinbe.domain.simulation.domain.SimulationSession;
import org.gp.newspinbe.domain.simulation.repository.SimulationSessionRepository;
import org.gp.newspinbe.domain.user.domain.User;
import org.gp.newspinbe.domain.user.repository.UserRepository;
import org.gp.newspinbe.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * I-4 / C-4. Gemini 호출이 실패해도(테스트 프로필은 더미 키) 리포트는 500 없이 반환되고,
 * 4개 섹션에 fallback 메시지가 채워진다 — 마커 파싱 실패로 "파싱할 수 없습니다" 가 나오던 것 대체.
 */
@IntegrationTest
class InvestmentReportFallbackTest {

    @Autowired InvestmentReportService reportService;
    @Autowired UserRepository userRepository;
    @Autowired SimulationSessionRepository sessionRepository;

    @Test
    void Gemini_실패시_리포트는_fallback_섹션으로_반환된다() {
        User user = userRepository.save(User.create("report-" + System.nanoTime() + "@t.com", "x"));
        SimulationSession session = sessionRepository.save(SimulationSession.createSession(
                user, new BigDecimal("10000000"),
                LocalDate.parse("2020-02-03"), LocalDate.parse("2020-02-14")));

        InvestmentReportResponse report = reportService.generateReport(session.getSessionId(), user.getUserId());

        assertThat(report).isNotNull();
        assertThat(report.getOverallAnalysis()).isNotBlank();
        assertThat(report.getOverallAnalysis()).doesNotContain("파싱할 수 없습니다");
        // 4개 섹션이 모두 채워져 있다 (fallback 이든 실제 분석이든)
        assertThat(report.getNewsResponseAnalysis()).isNotBlank();
        assertThat(report.getRiskManagementAnalysis()).isNotBlank();
        assertThat(report.getImprovementSuggestions()).isNotBlank();
    }
}
