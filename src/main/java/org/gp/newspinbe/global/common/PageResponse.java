package org.gp.newspinbe.global.common;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * 목록 API 페이지네이션 응답 (I-10). Spring 의 {@code PageImpl} 을 그대로 직렬화하면
 * 구조가 불안정하다는 경고가 나므로, 필요한 필드만 담은 안정적인 계약으로 감싼다.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }
}
