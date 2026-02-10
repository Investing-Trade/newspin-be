package org.gp.newspinbe.domain.event.service;

import java.time.LocalDate;
import java.util.Optional;

import org.gp.newspinbe.domain.event.domain.MarketEvent;
import org.gp.newspinbe.domain.event.repository.MarketEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final MarketEventRepository marketEventRepository;

    /**
     * 해당 날짜의 이벤트 조회 (없을 수도 있음)
     */
    public Optional<MarketEvent> findEventByDate(LocalDate date) {
        return marketEventRepository.findByEventDate(date);
    }
}
