package com.example.kiwi_backend.common.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserMessageListener {
    private final SimpMessagingTemplate messages;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(UserMessageEvent event) {
        try {
            messages.convertAndSendToUser(event.memberId().toString(), event.destination(), event.payload());
        } catch (RuntimeException e) {
            log.error("User message failed: memberId={}, destination={}", event.memberId(), event.destination(), e);
        }
    }
}
