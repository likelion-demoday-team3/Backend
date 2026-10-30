package com.example.kiwi_backend.interaction.application;

import com.example.kiwi_backend.common.exception.ContactUnavailableException;
import com.example.kiwi_backend.common.realtime.UserMessageEvent;
import com.example.kiwi_backend.contract.command.*;
import com.example.kiwi_backend.contract.dto.*;
import com.example.kiwi_backend.contract.query.*;
import com.example.kiwi_backend.interaction.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ContactService {
    private final SessionLiveAccess access;
    private final MatchQueryService matches;
    private final MemberContactQuery contacts;
    private final PostSessionWindowService windows;
    private final ContactShareRepository shares;
    private final ContactRequestRepository requests;
    private final GemWallet wallet;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SessionQueryService sessionQueries;

    @Transactional
    public ShareView send(Long sessionId, Long sender, Long receiver, ContactType type) {
        requirePair(sessionId, sender, receiver);
        var existing = shares.findBySessionIdAndSenderIdAndReceiverId(sessionId, sender, receiver);
        if (existing.isPresent()) return shareView(existing.get());
        windows.requireContactOpen(sessionId, sender);
        requireContact(sender, type);
        debit(sender, sessionId, GemTransactionType.CONTACT_SEND, "CONTACT_SEND:s" + sessionId + ":f" + sender + ":t" + receiver);
        return shareView(saveShare(sessionId, sender, receiver, type, ContactShare.ShareType.MANUAL));
    }

    @Transactional
    public RequestView request(Long sessionId, Long requester, Long target, ContactType type) {
        requirePair(sessionId, requester, target);
        var existing = requests.findBySessionIdAndRequesterIdAndTargetId(sessionId, requester, target);
        if (existing.isPresent()) return requestView(existing.get());
        windows.requireContactOpen(sessionId, requester);
        if (shares.findBySessionIdAndSenderIdAndReceiverId(sessionId, target, requester).isPresent())
            throw new IllegalStateException("Contact already available");
        requireTargetContact(target, type);
        var request = requests.saveAndFlush(ContactRequest.create(sessionId, requester, target, type, clock.instant()));
        events.publishEvent(new UserMessageEvent(target, "/queue/live/contact-requested", requestView(request)));
        return requestView(request);
    }

    @Transactional
    public RequestView respond(Long sessionId, Long requestId, Long memberId, boolean accept) {
        var context = access.lock(sessionId);
        context.requireParticipant(memberId);
        var request = requests.findById(requestId).orElseThrow();
        if (!request.getSessionId().equals(sessionId) || !request.getTargetId().equals(memberId))
            throw new IllegalArgumentException("Only the requested recipient may respond");
        if (request.getStatus() != ContactRequest.Status.PENDING) return requestView(request);
        int cost = 0;
        if (accept) {
            requireContact(memberId, request.getContactType());
            if (shares.findBySessionIdAndSenderIdAndReceiverId(sessionId, memberId, request.getRequesterId()).isEmpty()) {
                debit(request.getRequesterId(), sessionId, GemTransactionType.CONTACT_REQUEST,
                        "CONTACT_REQUEST:" + requestId);
                saveShare(sessionId, memberId, request.getRequesterId(), request.getContactType(), ContactShare.ShareType.REQUEST_ACCEPTED);
                cost = 1;
            }
        }
        request.respond(accept, cost, clock.instant());
        events.publishEvent(new UserMessageEvent(request.getRequesterId(), "/queue/live/contact-request-answered", requestView(request)));
        return requestView(request);
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onMatched(MatchingCompletedEvent event) {
        access.lock(event.sessionId());
        for (var pair : event.pairs()) {
            sharePreferred(event.sessionId(), pair.memberAId(), pair.memberBId());
            sharePreferred(event.sessionId(), pair.memberBId(), pair.memberAId());
        }
    }

    private void sharePreferred(Long sessionId, Long sender, Long receiver) {
        if (shares.findBySessionIdAndSenderIdAndReceiverId(sessionId, sender, receiver).isPresent()) return;
        var available = contacts.contacts(sender);
        ContactType preferred = available.containsKey(ContactType.PHONE) ? ContactType.PHONE : ContactType.INSTAGRAM;
        if (available.containsKey(preferred)) saveShare(sessionId, sender, receiver, preferred, ContactShare.ShareType.AUTO_MATCH);
    }

    private void requirePair(Long sessionId, Long sender, Long receiver) {
        var context = access.lock(sessionId);
        context.requireOppositePair(sender, receiver);
        if (context.phase() != LiveSessionContext.Phase.RESULT_AND_SHARE) throw new IllegalStateException("Contacts open after results only");
        if (matches.matchedPartner(sessionId, sender).filter(receiver::equals).isPresent())
            throw new IllegalStateException("Matched contacts are provided free");
    }

    private void requireContact(Long memberId, ContactType type) {
        if (type == null || !contacts.contacts(memberId).containsKey(type))
            throw new IllegalArgumentException("Requested contact is unavailable");
    }

    /** 요청할 상대가 그 종류를 등록하지 않았으면 CONTACT_UNAVAILABLE로 알려 프론트가 다른 종류를 제안하게 한다 */
    private void requireTargetContact(Long target, ContactType type) {
        if (type == null) throw new IllegalArgumentException("Requested contact is unavailable");
        if (!contacts.contacts(target).containsKey(type)) throw new ContactUnavailableException(type);
    }

    private void debit(Long memberId, Long sessionId, GemTransactionType type, String key) {
        wallet.apply(new GemCommand(memberId, type, 1, sessionId, null, null, key));
    }

    private ContactShare saveShare(Long sessionId, Long sender, Long receiver, ContactType type, ContactShare.ShareType shareType) {
        var share = shares.saveAndFlush(ContactShare.create(sessionId, sender, receiver, type, shareType, clock.instant()));
        // Do not put contact values in transient notification payloads or logs.
        events.publishEvent(new UserMessageEvent(receiver, "/queue/live/contact-received",
                new ContactReceived(sessionId, share.getId())));
        return share;
    }

    @Transactional(readOnly = true)
    public History<ShareView> shareHistory(Long memberId, int page, int size) {
        return shareHistory(memberId, null, page, size);
    }

    /** direction이 없으면 보낸 것과 받은 것 모두, SENT/RECEIVED면 한쪽만 */
    @Transactional(readOnly = true)
    public History<ShareView> shareHistory(Long memberId, Direction direction, int page, int size) {
        var pageable = page(page, size);
        var result = direction == null ? shares.history(memberId, pageable)
                : direction == Direction.SENT ? shares.sentHistory(memberId, pageable)
                : shares.receivedHistory(memberId, pageable);
        // 같은 세션 기록이 여러 건이어도 세션 정보는 한 번만 조회한다
        Map<Long, SessionInfo> cache = new HashMap<>();
        return new History<>(result.map(share -> shareView(share, cache)).getContent(), result.hasNext());
    }

    @Transactional(readOnly = true)
    public History<RequestView> requestHistory(Long memberId, int page, int size) {
        var result = requests.history(memberId, page(page, size));
        return new History<>(result.map(this::requestView).getContent(), result.hasNext());
    }

    private PageRequest page(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size)));
    }

    private ShareView shareView(ContactShare share) {
        return shareView(share, new HashMap<>());
    }

    private ShareView shareView(ContactShare share, Map<Long, SessionInfo> cache) {
        String value = contacts.contacts(share.getSenderId()).get(share.getContactType());
        var session = cache.computeIfAbsent(share.getSessionId(), this::sessionInfo);
        return new ShareView(share.getId(), share.getSessionId(), share.getSenderId(), share.getReceiverId(),
                share.getContactType(), value, share.getShareType(), share.getCreatedAt(),
                session.startTime(), session.aliases().get(share.getSenderId()), session.aliases().get(share.getReceiverId()));
    }

    private SessionInfo sessionInfo(Long sessionId) {
        Map<Long, String> aliases = new HashMap<>();
        sessionQueries.getLiveParticipants(sessionId).forEach(p -> aliases.put(p.memberId(), p.alias()));
        return new SessionInfo(sessionQueries.getStartTime(sessionId), aliases);
    }

    private record SessionInfo(Instant startTime, Map<Long, String> aliases) { }

    private RequestView requestView(ContactRequest request) {
        return new RequestView(request.getId(), request.getSessionId(), request.getRequesterId(), request.getTargetId(),
                request.getContactType(), request.getStatus(), request.getChargedGem(), request.getCreatedAt(), request.getRespondedAt());
    }

    public enum Direction { SENT, RECEIVED }
    public record ContactReceived(Long sessionId, Long shareId) { }
    public record History<T>(List<T> items, boolean hasNext) { }
    public record ShareView(Long id, Long sessionId, Long senderId, Long receiverId, ContactType contactType,
                            String contactValue, ContactShare.ShareType shareType, Instant createdAt,
                            Instant sessionStartTime, String senderAlias, String receiverAlias) { }
    public record RequestView(Long id, Long sessionId, Long requesterId, Long targetId, ContactType contactType,
                              ContactRequest.Status status, int chargedGem, Instant createdAt, Instant respondedAt) { }
}
