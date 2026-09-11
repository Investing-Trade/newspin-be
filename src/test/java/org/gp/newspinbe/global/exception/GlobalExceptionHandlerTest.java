package org.gp.newspinbe.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.gp.newspinbe.global.common.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * I-16. validation 에러가 필드별 메시지 없이 "유효성 검사가 실패했습니다."로 뭉뚱그려지던 것을
 * {필드명: 메시지} 맵으로 반환하도록 수정 — 순수 단위 테스트 (Spring 컨텍스트 없이 핸들러 메서드만 검증).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 필드별_검증_실패_메시지를_data에_담아_반환한다() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "email", "이메일 형식이 올바르지 않습니다."));
        bindingResult.addError(new FieldError("target", "password", "비밀번호는 8자 이상이어야 합니다."));
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(mock(MethodParameter.class), bindingResult);

        ResponseEntity<ApiResponse<Map<String, String>>> response =
                handler.handleMethodArgumentNotValidException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiResponse<Map<String, String>> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getCode());
        assertThat(body.getData())
                .containsEntry("email", "이메일 형식이 올바르지 않습니다.")
                .containsEntry("password", "비밀번호는 8자 이상이어야 합니다.");
    }

    /**
     * 로그인 실패(잘못된 비밀번호/존재하지 않는 이메일)가 이 핸들러 없이는 catch-all(Exception.class)로
     * 떨어져 500 "서버 내부 오류가 발생했습니다."로 응답했다 — newspin-web 실사용 테스트 중 발견.
     * BadCredentialsException/UsernameNotFoundException 모두 AuthenticationException 의
     * 하위 타입이라 한 핸들러로 둘 다 잡힌다(계정 존재 여부를 구분해 노출하지 않는 게 보안상 맞음).
     */
    @Test
    void 로그인_실패는_401과_전용_메시지로_응답한다() {
        ResponseEntity<ApiResponse<Void>> wrongPassword =
                handler.handleAuthenticationException(new BadCredentialsException("bad credentials"));
        ResponseEntity<ApiResponse<Void>> noSuchUser =
                handler.handleAuthenticationException(new UsernameNotFoundException("no such user"));

        for (ResponseEntity<ApiResponse<Void>> response : java.util.List.of(wrongPassword, noSuchUser)) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.LOGIN_FAILED.getCode());
            assertThat(response.getBody().getMessage()).isEqualTo("이메일 또는 비밀번호가 일치하지 않습니다.");
        }
    }
}
