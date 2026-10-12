package com.example.kiwi_backend.contract.dto;

/**
 * 라이브(로비 마감 후)에 참여하는 출석자 한 명.
 *
 * [제안 3] 원래 A의 live/domain/LiveParticipant(회원 id, 성별)를 contract/dto로 옮긴 것.
 *   - 위치: SessionQueryService(contract)가 이 타입을 반환하는데, live 패키지에 있으면
 *     구현하는 session이 live를 import해야 해서 모듈 규칙이 깨진다. 그래서 contract로 옮김
 *   - alias 추가: 라운드 배정 메시지에서 상대를 "졸린 수달"처럼 보여주려면 A가 별칭을 알아야 해서 추가
 *   A 쪽 작업: live/domain/LiveParticipant 삭제, import를 이 클래스로 변경, 생성 시 alias 인자 추가
 */
public record LiveParticipant(
        Long memberId,
        Gender gender,
        String alias
) {
    public LiveParticipant {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId must not be null");
        }
        if (gender == null) {
            throw new IllegalArgumentException("gender must not be null");
        }
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("alias must not be blank");
        }
    }
}
