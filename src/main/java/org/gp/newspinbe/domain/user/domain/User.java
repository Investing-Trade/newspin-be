package org.gp.newspinbe.domain.user.domain;

import org.gp.newspinbe.global.common.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long userId;

	private String email;

	private String password;

	private User(String email, String password) {
		this.email = email;
		this.password = password;
	}

	public static User create(String email, String password) {
		return new User(email, password);
	}

	public void updatePassword(String newPassword) {
		this.password = newPassword;
	}
}
