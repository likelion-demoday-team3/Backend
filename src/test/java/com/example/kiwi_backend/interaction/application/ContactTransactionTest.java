package com.example.kiwi_backend.interaction.application;

import com.example.kiwi_backend.common.exception.ContactUnavailableException;
import com.example.kiwi_backend.contract.dto.*;
import com.example.kiwi_backend.contract.query.MemberContactQuery;
import com.example.kiwi_backend.interaction.domain.*;
import com.example.kiwi_backend.session.domain.entity.*;
import com.example.kiwi_backend.session.domain.repository.*;
import com.example.kiwi_backend.wallet.application.service.WalletService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class ContactTransactionTest {
    @Autowired ContactService service;
    @Autowired PostSessionWindowService windows;
    @Autowired DatingSessionRepository sessions;
    @Autowired SessionParticipantRepository participants;
    @Autowired ContactShareRepository shares;
    @Autowired ContactRequestRepository requests;
    @Autowired WalletService wallet;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean MemberContactQuery contactQuery;
    private Long sessionId;
    private long sender;
    private long target;

    @BeforeEach
    void setup() {
        when(contactQuery.contacts(anyLong())).thenReturn(Map.of(ContactType.PHONE, "01000000000"));
        Instant now = Instant.now();
        var session = DatingSession.create(now.minusSeconds(600), 3, 180, 120, 300, 12, 2, now.minusSeconds(7200));
        session.openRecruiting(now.minusSeconds(7200));
        session.markReady(); session.startProgress(); session.markRoundsStarted();
        session.startHearts(now.minusSeconds(181)); session.startVoting(now.minusSeconds(121)); session.finishVoting(now);
        sessionId = sessions.saveAndFlush(session).getId();
        sender = 70000 + sessionId * 10;
        target = sender + 1;
        for (long id : List.of(sender, target, sender + 2)) {
            var p = SessionParticipant.apply(sessionId, id, id == sender ? Gender.MALE : Gender.FEMALE, "alias-" + id, now);
            p.attend(); participants.saveAndFlush(p); wallet.createWallet(id);
        }
    }

    @Test
    void sendDebitsOnceAndHistoryIsPrivate() {
        windows.closeResults(sessionId, sender);
        wallet.apply(GemCommand.adminGrant(sender, 2, "test", UUID.randomUUID().toString()));
        var receipt = service.send(sessionId, sender, target, ContactType.PHONE);
        assertThat(service.send(sessionId, sender, target, ContactType.PHONE).id()).isEqualTo(receipt.id());
        assertThat(wallet.getBalance(sender).gemBalance()).isEqualTo(1);
        assertThat(service.shareHistory(target, 0, 20).items()).extracting(ContactService.ShareView::contactValue)
                .containsExactly("01000000000");
        // 기록 화면에 상대 닉네임과 소개팅 일시를 보여줄 수 있어야 한다
        var received = service.shareHistory(target, 0, 20).items().getFirst();
        assertThat(received.senderAlias()).isEqualTo("alias-" + sender);
        assertThat(received.receiverAlias()).isEqualTo("alias-" + target);
        assertThat(received.sessionStartTime()).isEqualTo(sessions.findById(sessionId).orElseThrow().getStartTime());
        assertThat(service.shareHistory(sender, ContactService.Direction.SENT, 0, 20).items()).hasSize(1);
        assertThat(service.shareHistory(sender, ContactService.Direction.RECEIVED, 0, 20).items()).isEmpty();
        assertThat(service.shareHistory(target, ContactService.Direction.RECEIVED, 0, 20).items()).hasSize(1);
        assertThat(service.shareHistory(sender + 2, 0, 20).items()).isEmpty();
        windows.closeContacts(sessionId, sender);
        assertThatThrownBy(() -> service.send(sessionId, sender, sender + 2, ContactType.PHONE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptanceChargesRequesterOnlyAndWorksAfterRecipientClosesWindows() {
        windows.closeResults(sessionId, sender);
        wallet.apply(GemCommand.adminGrant(sender, 1, "test", UUID.randomUUID().toString()));
        var request = service.request(sessionId, sender, target, ContactType.PHONE);
        assertThat(wallet.getBalance(sender).gemBalance()).isEqualTo(1);
        windows.closeResults(sessionId, target); windows.closeContacts(sessionId, target);
        assertThatThrownBy(() -> service.respond(sessionId, request.id(), sender, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.respond(sessionId, request.id(), target, true).status()).isEqualTo(ContactRequest.Status.ACCEPTED);
        service.respond(sessionId, request.id(), target, true);
        assertThat(wallet.getBalance(sender).gemBalance()).isZero();
        assertThat(wallet.getBalance(target).gemBalance()).isZero();
        assertThat(service.shareHistory(sender, 0, 20).items()).hasSize(1);
    }

    @Test
    void insufficientBalanceLeavesRequestPendingAndContactHidden() {
        windows.closeResults(sessionId, sender);
        var request = service.request(sessionId, sender, target, ContactType.PHONE);
        assertThatThrownBy(() -> service.respond(sessionId, request.id(), target, true)).isInstanceOf(RuntimeException.class);
        assertThat(requests.findById(request.id()).orElseThrow().getStatus()).isEqualTo(ContactRequest.Status.PENDING);
        assertThat(service.shareHistory(sender, 0, 20).items()).isEmpty();
        service.respond(sessionId, request.id(), target, false);
        assertThat(wallet.getBalance(sender).gemBalance()).isZero();
        assertThat(requests.findById(request.id()).orElseThrow().getStatus()).isEqualTo(ContactRequest.Status.REJECTED);
    }

    @Test
    void automaticSharingIsFreeAndIdempotent() {
        var event = new MatchingCompletedEvent(sessionId, List.of(new MatchedPair(sender, target)));
        var transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> events.publishEvent(event));
        transaction.executeWithoutResult(status -> events.publishEvent(event));
        assertThat(shares.findBySessionIdAndSenderIdAndReceiverId(sessionId, sender, target)).isPresent();
        assertThat(shares.findBySessionIdAndSenderIdAndReceiverId(sessionId, target, sender)).isPresent();
        assertThat(wallet.getBalance(sender).gemBalance()).isZero();
        assertThat(service.shareHistory(target, 0, 20).items()).hasSize(2);
    }

    @Test
    void requestingUnregisteredContactTypeTellsWhichTypeIsMissingAndSavesNothing() {
        windows.closeResults(sessionId, sender);
        // 상대는 전화번호만 등록했다 (인스타그램 없음)
        when(contactQuery.contacts(target)).thenReturn(Map.of(ContactType.PHONE, "01000000000"));

        assertThatThrownBy(() -> service.request(sessionId, sender, target, ContactType.INSTAGRAM))
                .isInstanceOf(ContactUnavailableException.class)
                .hasMessage("상대방의 인스타그램 아이디가 등록되어 있지 않아요")
                .extracting(e -> ((ContactUnavailableException) e).getRequested())
                .isEqualTo(ContactType.INSTAGRAM);
        assertThat(requests.findBySessionIdAndRequesterIdAndTargetId(sessionId, sender, target)).isEmpty();

        // 프론트가 "대신 전화번호를 요청할까요?"에서 예를 누르면 그대로 요청된다
        assertThat(service.request(sessionId, sender, target, ContactType.PHONE).status())
                .isEqualTo(ContactRequest.Status.PENDING);
    }

    @Test
    void contactWindowCannotOpenBeforeResultClosure() {
        assertThatThrownBy(() -> service.send(sessionId, sender, target, ContactType.PHONE))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> windows.closeContacts(sessionId, sender))
                .isInstanceOf(IllegalStateException.class);
        assertThat(windows.state(sessionId, sender).resultsClosed()).isFalse();
    }
}
