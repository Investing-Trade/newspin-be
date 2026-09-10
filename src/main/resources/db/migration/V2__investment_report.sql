-- 투자 리포트 비동기 생성 (I-11). AI 분석은 백그라운드에서 생성되어 여기 저장된다.
CREATE TABLE investment_report (
    report_id                BIGINT NOT NULL AUTO_INCREMENT,
    session_id               BIGINT NOT NULL,
    status                   ENUM ('FAILED','GENERATING','READY') NOT NULL,
    overall_analysis         TEXT,
    news_response_analysis   TEXT,
    risk_management_analysis  TEXT,
    improvement_suggestions   TEXT,
    error_message            VARCHAR(500),
    generated_at             DATETIME(6),
    created_at               DATETIME(6),
    updated_at               DATETIME(6),
    deleted_at               DATETIME(6),
    PRIMARY KEY (report_id),
    CONSTRAINT uk_investment_report_session UNIQUE (session_id),
    CONSTRAINT fk_investment_report_session FOREIGN KEY (session_id) REFERENCES simulation_session (session_id)
) ENGINE=InnoDB;
