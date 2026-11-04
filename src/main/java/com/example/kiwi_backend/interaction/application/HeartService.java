package com.example.kiwi_backend.interaction.application;

import com.example.kiwi_backend.common.realtime.UserMessageEvent;
import com.example.kiwi_backend.contract.command.*;
import com.example.kiwi_backend.contract.dto.*;
import com.example.kiwi_backend.contract.query.LiveEncounterQuery;
import com.example.kiwi_backend.interaction.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HeartService {
    private final SessionLiveAccess access;
    private final HeartRepository hearts;
    private final GemWallet wallet;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final LiveEncounterQuery encounters;

    @Transactional
    public Receipt send(Long sessionId, Long senderId, Long receiverId) {
        var context = access.lock(sessionId);
        context.requireOppositePair(senderId, receiverId);
        // 하트는 실제로 같은 방에서 대화한 상대에게만 보낼 수 있다
        if (!encounters.haveMet(sessionId, senderId, receiverId)) {
            throw new IllegalArgumentException("대화한 상대에게만 하트를 보낼 수 있어요");
        }
        var existing = hearts.findBySessionIdAndSenderIdAndReceiverId(sessionId, senderId, receiverId);
        if (existing.isPresent()) return new Receipt(receiverId, existing.get().getChargedGem(), true);
        if (context.phase() != LiveSessionContext.Phase.HEART || context.deadline() == null
                || !clock.instant().isBefore(context.deadline())) {
            throw new IllegalStateException("Heart sending is closed");
        }
        int price = hearts.findAllBySessionIdAndSenderId(sessionId, senderId).isEmpty() ? 0 : 1;
        if (price > 0) {
            wallet.apply(new GemCommand(senderId, GemTransactionType.HEART_SEND, price, sessionId,
                    null, null, "HEART:s" + sessionId + ":f" + senderId + ":t" + receiverId));
        }
        hearts.saveAndFlush(Heart.create(sessionId, senderId, receiverId, price));
        events.publishEvent(new UserMessageEvent(receiverId, "/queue/live/heart-received",
                new Received(sessionId, senderId, context.requireParticipant(senderId).alias(),
                        hearts.countBySessionIdAndReceiverId(sessionId, receiverId))));
        return new Receipt(receiverId, price, false);
    }

    @Transactional
    public HeartStatus status(Long sessionId, Long memberId) {
        var context = access.lock(sessionId);
        context.requireParticipant(memberId);
        var sent = hearts.findAllBySessionIdAndSenderId(sessionId, memberId);
        var receivedFrom = hearts.findAllBySessionIdAndReceiverId(sessionId, memberId).stream()
                .map(heart -> new HeartSender(heart.getSenderId(),
                        context.requireParticipant(heart.getSenderId()).alias()))
                .toList();
        return new HeartStatus(sent.stream().map(Heart::getReceiverId).toList(),
                receivedFrom.size(), receivedFrom, sent.isEmpty(),
                context.phase() == LiveSessionContext.Phase.HEART ? context.deadline() : null);
    }

    public record Receipt(Long receiverId, int chargedGem, boolean alreadySent) { }
    public record Received(Long sessionId, Long senderId, String senderAlias, long receivedCount) { }
    public record HeartSender(Long senderId, String senderAlias) { }
    public record HeartStatus(List<Long> sentTo, long receivedCount, List<HeartSender> receivedFrom,
                              boolean freeHeartRemaining, Instant heartsEndsAt) { }
}
