package com.example.kiwi_backend.session.application.support;

import com.example.kiwi_backend.common.notification.NotificationEvent;
import com.example.kiwi_backend.common.notification.NotificationTexts;
import com.example.kiwi_backend.common.notification.NotificationType;
import com.example.kiwi_backend.session.domain.entity.DatingSession;
import com.example.kiwi_backend.session.domain.entity.SessionParticipant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 세션 진행·취소 알림(알림함). 진행 판단·취소·로비 마감과 같은 트랜잭션에서 발행한다.
 * 한 세션의 취소 알림은 종류가 달라도 회원당 하나만 남도록 같은 키(SESSION_CANCELLED)를 쓴다.
 */
@Component
@RequiredArgsConstructor
public class SessionNotifications {

    private static final String RESERVATION_LINK = "/mypage/reservation";

    private final ApplicationEventPublisher events;

    /** 시작 30분 전 진행 확정 */
    public void confirmed(DatingSession session, List<SessionParticipant> applied) {
        String time = NotificationTexts.sessionTime(session.getStartTime());
        for (SessionParticipant participant : applied) {
            publish(participant, session, NotificationType.SESSION_CONFIRMED, "소개팅이 확정됐어요",
                    time + " 소개팅이 확정됐어요. 시작 5분 전부터 입장할 수 있고, 시작 시각까지 입장해야 참여할 수 있어요.",
                    "/session/" + session.getId(), "SESSION_CONFIRMED");
        }
    }

    /** 시작 30분 전 신청 인원 미달 취소. 신청자 전원 환불 */
    public void cancelledForLowApplicants(DatingSession session, List<SessionParticipant> applied) {
        String reason = NotificationTexts.sessionTime(session.getStartTime()) + " 소개팅이 신청 인원이 모자라 취소됐어요.";
        for (SessionParticipant participant : applied) {
            publish(participant, session, NotificationType.SESSION_CANCELLED_LOW_APPLICANTS, "소개팅이 취소됐어요",
                    reason + refundSentence(session), RESERVATION_LINK, "SESSION_CANCELLED");
        }
    }

    /** 운영자 취소(또는 신청자가 있어 삭제 대신 취소). 신청자 전원 환불 */
    public void cancelledByAdmin(DatingSession session, List<SessionParticipant> applied) {
        String reason = "운영 사정으로 " + NotificationTexts.sessionTime(session.getStartTime()) + " 소개팅이 취소됐어요.";
        for (SessionParticipant participant : applied) {
            publish(participant, session, NotificationType.SESSION_CANCELLED_BY_ADMIN, "소개팅이 취소됐어요",
                    reason + refundSentence(session), RESERVATION_LINK, "SESSION_CANCELLED");
        }
    }

    /** 로비 마감 때 출석 인원 미달 취소. 출석자만 환불되고 입장하지 않은 사람은 환불이 없다 */
    public void cancelledForLowAttendance(DatingSession session, List<SessionParticipant> attended,
                                          List<SessionParticipant> noShows) {
        String reason = NotificationTexts.sessionTime(session.getStartTime()) + " 소개팅이 입장 인원이 모자라 취소됐어요.";
        for (SessionParticipant participant : attended) {
            publish(participant, session, NotificationType.SESSION_CANCELLED_LOW_ATTENDANCE, "소개팅이 취소됐어요",
                    reason + refundSentence(session), RESERVATION_LINK, "SESSION_CANCELLED");
        }
        String noShowBody = reason + (session.getEntryFeeGem() > 0
                ? " 시작 시각까지 입장하지 않아 참가비는 환불되지 않아요."
                : "");
        for (SessionParticipant participant : noShows) {
            publish(participant, session, NotificationType.SESSION_CANCELLED_LOW_ATTENDANCE, "소개팅이 취소됐어요",
                    noShowBody, RESERVATION_LINK, "SESSION_CANCELLED");
        }
    }

    /** 세션은 진행됐는데 시작 시각까지 입장하지 않음 */
    public void noShow(DatingSession session, List<SessionParticipant> noShows) {
        String body = NotificationTexts.sessionTime(session.getStartTime())
                + " 소개팅에 시작 시각까지 입장하지 않아 불참 처리됐어요."
                + (session.getEntryFeeGem() > 0 ? " 참가비는 환불되지 않아요." : "");
        for (SessionParticipant participant : noShows) {
            publish(participant, session, NotificationType.NO_SHOW, "소개팅에 불참 처리됐어요",
                    body, RESERVATION_LINK, "NO_SHOW");
        }
    }

    private static String refundSentence(DatingSession session) {
        return session.getEntryFeeGem() > 0 ? " 참가비 ◆" + session.getEntryFeeGem() + "개를 돌려드렸어요." : "";
    }

    private void publish(SessionParticipant participant, DatingSession session, NotificationType type,
                         String title, String body, String link, String keyPrefix) {
        events.publishEvent(new NotificationEvent(participant.getMemberId(), type, title, body, session.getId(), link,
                keyPrefix + ":s" + session.getId() + ":m" + participant.getMemberId()));
    }
}
