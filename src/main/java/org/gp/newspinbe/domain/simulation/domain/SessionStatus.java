package org.gp.newspinbe.domain.simulation.domain;

public enum SessionStatus {
    ACTIVE("진행 중"),
    COMPLETED("완료"),
    ABANDONED("중단");

    private final String description;

    SessionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
