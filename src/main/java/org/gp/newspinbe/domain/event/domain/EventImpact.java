package org.gp.newspinbe.domain.event.domain;

/**
 * 이벤트의 전반적인 영향도
 */
public enum EventImpact {
    POSITIVE("긍정"), // 전반적으로 시장에 호재
    NEGATIVE("부정"), // 전반적으로 시장에 악재
    MIXED("혼합"); // 업종별로 다름 (일부는 호재, 일부는 악재)

    private final String description;

    EventImpact(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
