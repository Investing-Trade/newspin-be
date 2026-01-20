package org.gp.newspinbe.domain.user.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EmailVerificationResponse {
	Boolean verified;
	String message;
}
