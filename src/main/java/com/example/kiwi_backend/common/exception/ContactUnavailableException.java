package com.example.kiwi_backend.common.exception;

import com.example.kiwi_backend.contract.dto.ContactType;

/**
 * 상대가 등록하지 않은 종류의 연락처를 요청함.
 * 프론트가 "대신 다른 연락처를 요청할까요?" 안내를 띄울 수 있게 별도 에러 코드(CONTACT_UNAVAILABLE)로 응답한다.
 * 회원은 전화번호·인스타그램 중 하나는 반드시 등록하므로, 요청한 종류가 없으면 나머지 하나는 항상 있다.
 */
public class ContactUnavailableException extends IllegalArgumentException {

    private final ContactType requested;

    public ContactUnavailableException(ContactType requested) {
        super(messageFor(requested));
        this.requested = requested;
    }

    public ContactType getRequested() {
        return requested;
    }

    private static String messageFor(ContactType requested) {
        return requested == ContactType.PHONE
                ? "상대방의 전화번호가 등록되어 있지 않아요"
                : "상대방의 인스타그램 아이디가 등록되어 있지 않아요";
    }
}
