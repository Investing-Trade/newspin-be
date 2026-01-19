package org.gp.newspinbe.domain.user.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignUpRequest {
	private String userId;
	private String email;
	private String password;
}
