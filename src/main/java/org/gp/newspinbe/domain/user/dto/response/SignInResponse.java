package org.gp.newspinbe.domain.user.dto.response;

import org.gp.newspinbe.global.security.jwt.JwtToken;

import lombok.AllArgsConstructor;
import lombok.Getter;


@Getter
@AllArgsConstructor
public class SignInResponse {
	private JwtToken jwtToken;
}
