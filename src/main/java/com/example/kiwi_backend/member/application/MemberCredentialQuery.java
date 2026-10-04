package com.example.kiwi_backend.member.application;

import java.util.Optional;

public interface MemberCredentialQuery {

    Optional<MemberCredential> findByEmail(String email);
}
