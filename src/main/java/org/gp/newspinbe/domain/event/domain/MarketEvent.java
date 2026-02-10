package org.gp.newspinbe.domain.event.domain;

import java.time.LocalDate;

import org.gp.newspinbe.global.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시장 이벤트 (코로나 백신, 금리 인상 등 주요 이벤트)
 * 사용자에게는 뉴스로만 제공되고, 영향도는 숨김
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "market_event", indexes = {
        @Index(name = "idx_event_date", columnList = "event_date")
})
public class MarketEvent extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventId;

    @Column(nullable = false, length = 200)
    private String eventName; // 이벤트 이름 (예: "코로나19 백신 개발 성공")

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate; // 이벤트 발생 날짜

    @Column(columnDefinition = "TEXT")
    private String description; // 상세 설명

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventType eventType; // 이벤트 유형

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventImpact overallImpact; // 전반적인 영향도 (긍정/부정/혼합)

    private MarketEvent(String eventName, LocalDate eventDate, String description,
            EventType eventType, EventImpact overallImpact) {
        this.eventName = eventName;
        this.eventDate = eventDate;
        this.description = description;
        this.eventType = eventType;
        this.overallImpact = overallImpact;
    }

    public static MarketEvent createEvent(String eventName, LocalDate eventDate,
            String description, EventType eventType,
            EventImpact overallImpact) {
        return new MarketEvent(eventName, eventDate, description, eventType, overallImpact);
    }

    public void updateEvent(String eventName, String description, EventType eventType,
            EventImpact overallImpact) {
        this.eventName = eventName;
        this.description = description;
        this.eventType = eventType;
        this.overallImpact = overallImpact;
    }
}
