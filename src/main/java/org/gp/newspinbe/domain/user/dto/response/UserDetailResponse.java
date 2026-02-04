package org.gp.newspinbe.domain.user.dto.response;

import org.gp.newspinbe.domain.user.domain.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class UserDetailResponse {
    private Long userId;
    private String email;

    public static UserDetailResponse from(User user) {
        return UserDetailResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .build();
    }
}
