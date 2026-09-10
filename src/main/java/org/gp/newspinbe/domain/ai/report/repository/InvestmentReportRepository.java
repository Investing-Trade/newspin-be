package org.gp.newspinbe.domain.ai.report.repository;

import java.util.Optional;

import org.gp.newspinbe.domain.ai.report.domain.InvestmentReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentReportRepository extends JpaRepository<InvestmentReport, Long> {

    Optional<InvestmentReport> findBySessionId(Long sessionId);
}
