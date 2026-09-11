package org.gp.newspinbe.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.gp.newspinbe.domain.user.application.UserService;
import org.gp.newspinbe.domain.user.dto.request.PasswordResetRequest;
import org.gp.newspinbe.domain.user.dto.request.SignInRequest;
import org.gp.newspinbe.domain.user.dto.request.SignUpRequest;
import org.gp.newspinbe.domain.user.dto.response.EmailVerificationResponse;
import org.gp.newspinbe.global.exception.CustomException;
import org.gp.newspinbe.global.exception.ErrorCode;
import org.gp.newspinbe.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;

import jakarta.mail.internet.MimeMessage;

/**
 * SMTP host 설정 수정(라운드 2). 이 전까지 이메일 인증/비밀번호 재설정은 host 가 없어
 * 항상 EXTERNAL_SERVICE_ERROR 로 실패했고, 그 경로를 실제로 태우는 테스트도 없었다.
 * GreenMail(가짜 SMTP, application-test.yml 의 mail.port=3025 와 이미 일치)로 실제
 * 발송~수신까지 검증한다.
 */
@IntegrationTest
class EmailFlowTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication());

    @Autowired UserService userService;
    private final ObjectMapper om = new ObjectMapper();

    private static final String PW = "pw1234!@";

    /** "인증번호: XXXXXX" / "비밀번호 재설정 인증번호: XXXXXX" 뒤 6자리 코드 추출. */
    private String extractCode(MimeMessage message) throws Exception {
        String body = GreenMailUtil.getBody(message);
        String[] parts = body.trim().split(":\\s*");
        return parts[parts.length - 1].trim();
    }

    @Test
    void 회원가입_인증코드가_실제로_발송되고_검증까지_이어진다() throws Exception {
        String email = "verify-" + System.nanoTime() + "@t.com";

        userService.sendVerificationEmail(email);

        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertThat(messages).hasSize(1);
        assertThat(messages[0].getSubject()).contains("인증");
        assertThat(messages[0].getAllRecipients()[0].toString()).isEqualTo(email);

        String code = extractCode(messages[0]);
        assertThat(code).hasSize(6);
        EmailVerificationResponse verified = userService.verifyEmail(email, code);
        assertThat(verified.getVerified()).isTrue();

        EmailVerificationResponse wrong = userService.verifyEmail(email, "WRONG1");
        assertThat(wrong.getVerified()).isFalse();
    }

    @Test
    void 이미_가입된_이메일로_인증코드를_요청하면_거절되고_메일도_안_간다() {
        String email = "dup-" + System.nanoTime() + "@t.com";
        userService.signUp(om.convertValue(Map.of("email", email, "password", PW), SignUpRequest.class));

        assertThatThrownBy(() -> userService.sendVerificationEmail(email))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.EMAIL_DUPLICATION));
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void 비밀번호_재설정_코드가_발송되고_재설정_후_새_비밀번호로_로그인된다() throws Exception {
        String email = "reset-" + System.nanoTime() + "@t.com";
        userService.signUp(om.convertValue(Map.of("email", email, "password", PW), SignUpRequest.class));

        userService.sendPasswordResetCode(email);
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertThat(messages).hasSize(1);
        assertThat(messages[0].getSubject()).contains("비밀번호");

        String code = extractCode(messages[0]);
        assertThat(code).hasSize(6);
        String newPassword = "newpw123!";
        userService.resetPassword(new PasswordResetRequest(email, code, newPassword));

        assertThat(userService.signIn(
                om.convertValue(Map.of("email", email, "password", newPassword), SignInRequest.class)))
                .isNotNull();
    }
}
