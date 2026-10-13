package com.example.kiwi_backend.contract.command;
public interface ResultWindowAccess {
    void requireResultOpen(Long sessionId, Long memberId);
}
