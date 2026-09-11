package org.gp.newspinbe.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.gp.newspinbe.global.common.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
}
