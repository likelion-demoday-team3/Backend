package com.example.kiwi_backend.common.exception;

/** 로그인 실패. 401로 변환된다. */
public class AuthenticationFailedException extends RuntimeException{
    public AuthenticationFailedException(String message){
        super(message);
    }
}
