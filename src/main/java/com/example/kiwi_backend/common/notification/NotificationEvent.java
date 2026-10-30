package com.example.kiwi_backend.common.notification;

/**
 * 알림함에 알림 하나를 남겨 달라는 요청. session·interaction 등이 발행하고 notification 모듈이 저장한다.
 * 발행한 트랜잭션 안에서 저장되므로 취소·환불이 롤백되면 알림도 남지 않는다.
 *
 * dedupeKey: 스케줄러 재시도 등으로 같은 일이 두 번 처리돼도 알림은 한 번만 남게 하는 키
 * link: 알림을 눌렀을 때 이동할 프론트 경로 (예: /contact?sessionId=12). 없으면 null
 */
public record NotificationEvent(
        Long memberId,
        NotificationType type,
        String title,
        String body,
        Long sessionId,
        String link,
        String dedupeKey
) {
}
