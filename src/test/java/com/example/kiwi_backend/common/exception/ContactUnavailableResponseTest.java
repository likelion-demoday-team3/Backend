package com.example.kiwi_backend.common.exception;

import com.example.kiwi_backend.common.response.ApiResponse;
import com.example.kiwi_backend.contract.dto.ContactType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** 프론트는 code만 보고 "다른 연락처를 대신 요청할까요?"를 띄운다 */
class ContactUnavailableResponseTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void respondsWithDedicatedCodeAndKoreanMessage() {
        ResponseEntity<ApiResponse<Void>> phone = handler.handleContactUnavailable(
                new ContactUnavailableException(ContactType.PHONE));
        ResponseEntity<ApiResponse<Void>> instagram = handler.handleContactUnavailable(
                new ContactUnavailableException(ContactType.INSTAGRAM));

        assertThat(phone.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(phone.getBody().error().code()).isEqualTo("CONTACT_UNAVAILABLE");
        assertThat(phone.getBody().error().message()).isEqualTo("상대방의 전화번호가 등록되어 있지 않아요");
        assertThat(instagram.getBody().error().message()).isEqualTo("상대방의 인스타그램 아이디가 등록되어 있지 않아요");
    }
}
