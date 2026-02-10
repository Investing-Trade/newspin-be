package org.gp.newspinbe.domain.simulation.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SessionCreateRequest {

    @NotNull(message = "초기 자본금은 필수입니다")
    @Min(value = 1000000, message = "초기 자본금은 최소 100만원 이상이어야 합니다")
    private BigDecimal initialCapital;

    @NotNull(message = "시작 날짜는 필수입니다")
    private LocalDate startDate;

    @NotNull(message = "종료 날짜는 필수입니다")
    private LocalDate endDate;
}
