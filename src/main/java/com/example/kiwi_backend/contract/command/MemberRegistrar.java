package com.example.kiwi_backend.contract.command;

import com.example.kiwi_backend.contract.dto.RegisterMemberCommand;


/* RegisterMemberCommand는 "무엇을 넘기는지"에 대한 약속
 * MemberRegistrar는 "무엇을 해달라고 요청할 수 있는지"에 대한 약속
 * member/MemberRegistrarImpl이 구현체
 */
public interface MemberRegistrar {

    Long register(RegisterMemberCommand command);
}
