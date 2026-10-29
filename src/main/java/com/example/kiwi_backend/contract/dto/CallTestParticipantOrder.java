package com.example.kiwi_backend.contract.dto;

public record CallTestParticipantOrder(
        Long hostMemberId,
        Long firstPartnerMemberId,
        Long secondPartnerMemberId
) {
}
