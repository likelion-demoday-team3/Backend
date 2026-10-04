package com.example.kiwi_backend.member.application;

public interface MemberRegistrar {

    Long register(RegisterMemberCommand command);
}
