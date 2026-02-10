package org.gp.newspinbe.domain.event.domain;

public enum EventType {
    PANDEMIC("감염병"), // 코로나, 메르스 등
    POLICY("정책"), // 금리 인상, 규제 변화
    DISASTER("재난"), // 자연재해, 사고
    ECONOMIC("경제"), // 환율 변동, 경기 변화
    TECHNOLOGY("기술"), // 혁신 기술 발표
    GEOPOLITICAL("지정학"); // 전쟁, 국제 관계

    private final String description;

    EventType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
