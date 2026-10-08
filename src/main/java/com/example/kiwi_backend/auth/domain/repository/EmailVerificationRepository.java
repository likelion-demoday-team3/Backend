package com.example.kiwi_backend.auth.domain.repository;

import com.example.kiwi_backend.auth.domain.entity.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    /** 해당 이메일로 가장 최근에 보낸 인증 코드 */
    Optional<EmailVerification> findTopByEmailOrderByCreatedAtDesc(String email);
}
