package org.gp.newspinbe.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RefreshRequest {
	@NotBlank(message = "Refreshtoken 값이 존재하지 않습니다.")
	String refreshToken;
}
