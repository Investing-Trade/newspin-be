package org.gp.newspinbe.domain.event.dto.response;

import java.time.LocalDate;

import org.gp.newspinbe.domain.event.domain.EventType;
import org.gp.newspinbe.domain.event.domain.MarketEvent;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EventResponse {
    private String eventName;
    private String description;
    private LocalDate eventDate;
    private EventType eventType;

    public static EventResponse from(MarketEvent event) {
        return EventResponse.builder()
                .eventName(event.getEventName())
                .description(event.getDescription())
                .eventDate(event.getEventDate())
                .eventType(event.getEventType())
                .build();
    }
}
