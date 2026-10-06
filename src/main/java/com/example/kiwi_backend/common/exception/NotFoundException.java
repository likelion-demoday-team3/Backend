package com.example.kiwi_backend.common.exception;

/** 조회 대상이 없을 때. 404로 변환된다 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
