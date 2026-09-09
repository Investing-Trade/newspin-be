package org.gp.newspinbe.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.gp.newspinbe.domain.user.application.UserService;
import org.gp.newspinbe.domain.user.dto.request.RefreshRequest;
import org.gp.newspinbe.domain.user.dto.request.SignInRequest;
import org.gp.newspinbe.domain.user.dto.request.SignUpRequest;
import org.gp.newspinbe.global.exception.CustomException;
import org.gp.newspinbe.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;

/** I-7. 리프레시 토큰을 기기별로 저장 — 멀티 디바이스 로그인 + 회전. */
@IntegrationTest
class AuthRefreshTest {

    @Autowired UserService userService;
    private final ObjectMapper om = new ObjectMapper();

    private static final String PW = "pw1234!@";

    private String signInRefresh(String email) {
        return userService.signIn(om.convertValue(Map.of("email", email, "password", PW), SignInRequest.class))
                .getJwtToken().getRefreshToken();
    }

    private RefreshRequest req(String token) {
        return om.convertValue(Map.of("refreshToken", token), RefreshRequest.class);
    }

    @Test
    void 두_기기_로그인이_서로를_무효화하지_않는다() {
        String email = "authA-" + System.nanoTime() + "@t.com";
        userService.signUp(om.convertValue(Map.of("email", email, "password", PW), SignUpRequest.class));

        String deviceA = signInRefresh(email);
        String deviceB = signInRefresh(email); // 기존엔 이 시점에 A 가 덮여 무효화됐음

        assertThat(userService.refresh(req(deviceA))).isNotNull();
        assertThat(userService.refresh(req(deviceB))).isNotNull();
    }

    @Test
    void 리프레시하면_사용한_토큰은_회전되어_무효화된다() {
        String email = "authB-" + System.nanoTime() + "@t.com";
        userService.signUp(om.convertValue(Map.of("email", email, "password", PW), SignUpRequest.class));

        String old = signInRefresh(email);
        assertThat(userService.refresh(req(old))).isNotNull(); // 1회차: 성공 + 회전

        assertThatThrownBy(() -> userService.refresh(req(old)))
                .isInstanceOf(CustomException.class); // 같은 토큰 재사용 → 실패
    }
}
