package com.example.kiwi_backend.contract.query;

import com.example.kiwi_backend.contract.dto.MemberCredential;

import java.util.Optional;

public interface MemberCredentialQuery {

    Optional<MemberCredential> findByEmail(String email);
}
