package com.example.kiwi_backend.vote.application;

import com.example.kiwi_backend.contract.command.*;
import com.example.kiwi_backend.contract.dto.*;
import com.example.kiwi_backend.vote.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PaidResultService {
    private final SessionLiveAccess access;
    private final ResultWindowAccess windows;
    private final ResultPurchaseRepository purchases;
    private final VoteRepository votes;
    private final GemWallet wallet;
    private final Clock clock;

    @Transactional
    public PurchaseResult purchase(Long sessionId, Long buyer, ResultProduct product, Long target) {
        var context = access.lock(sessionId);
        windows.requireResultOpen(sessionId, buyer);
        long targetKey = validate(context, buyer, product, target);
        var owned = purchases.findAllBySessionIdAndBuyerId(sessionId, buyer);
        Details details = details(context, buyer, product, target);
        if (owns(owned, product, targetKey)) return new PurchaseResult(0, false, details);
        wallet.apply(new GemCommand(buyer, GemTransactionType.RESULT_PURCHASE, product.gems(), sessionId,
                null, null, "RESULT:s" + sessionId + ":m" + buyer + ":" + product + ":t" + targetKey));
        purchases.saveAndFlush(ResultPurchase.create(sessionId, buyer, product, targetKey, clock.instant()));
        return new PurchaseResult(product.gems(), true, details);
    }

    @Transactional
    public Details get(Long sessionId, Long buyer, ResultProduct product, Long target) {
        var context = access.lock(sessionId);
        windows.requireResultOpen(sessionId, buyer);
        long targetKey = validate(context, buyer, product, target);
        if (!owns(purchases.findAllBySessionIdAndBuyerId(sessionId, buyer), product, targetKey))
            throw new IllegalStateException("Purchase required");
        return details(context, buyer, product, target);
    }

    @Transactional
    public List<ProductInfo> catalog(Long sessionId, Long buyer) {
        access.lock(sessionId).requireParticipant(buyer);
        windows.requireResultOpen(sessionId, buyer);
        return Arrays.stream(ResultProduct.values()).map(p -> new ProductInfo(p, p.gems(), p.targetRequired())).toList();
    }

    private long validate(LiveSessionContext context, Long buyer, ResultProduct product, Long target) {
        context.requireParticipant(buyer);
        if (context.phase() != LiveSessionContext.Phase.RESULT_AND_SHARE || product == null)
            throw new IllegalStateException("Results not ready or invalid product");
        if (product.targetRequired()) {
            if (target == null || target.equals(buyer)) throw new IllegalArgumentException("Choose another participant");
            context.requireParticipant(target);
        } else if (target != null) throw new IllegalArgumentException("This product does not take a target");
        return target == null ? 0L : target;
    }

    private boolean owns(List<ResultPurchase> owned, ResultProduct product, long target) {
        if (owned.stream().anyMatch(p -> p.getProduct() == ResultProduct.ALL_RANKINGS)) return true;
        if (product == ResultProduct.BUNDLE) {
            return owns(owned, ResultProduct.FIRST_CHOOSERS, 0)
                    && owns(owned, ResultProduct.MY_FIRST_CHOICE_RANK, 0)
                    && owns(owned, ResultProduct.USER_RANKINGS, target);
        }
        return owned.stream().anyMatch(p ->
                (p.getProduct() == product && p.getTargetKey() == target)
                || (p.getProduct() == ResultProduct.BUNDLE
                    && (product == ResultProduct.FIRST_CHOOSERS || product == ResultProduct.MY_FIRST_CHOICE_RANK
                        || (product == ResultProduct.USER_RANKINGS && p.getTargetKey() == target))));
    }

    private Details details(LiveSessionContext context, Long buyer, ResultProduct product, Long target) {
        var all = votes.findAllBySessionIdOrderByVoterIdAscVoteRankAsc(context.sessionId());
        List<Person> firstChoosers = List.of();
        List<Ranking> rankings = List.of();
        MyRank myRank = null;
        if (product == ResultProduct.FIRST_CHOOSERS || product == ResultProduct.BUNDLE) {
            firstChoosers = all.stream().filter(v -> v.getVoteRank() == 1 && v.getTargetMemberId().equals(buyer))
                    .map(v -> person(context, v.getVoterId())).toList();
        }
        if (product == ResultProduct.MY_FIRST_CHOICE_RANK || product == ResultProduct.BUNDLE) {
            Long first = all.stream().filter(v -> v.getVoterId().equals(buyer) && v.getVoteRank() == 1)
                    .map(Vote::getTargetMemberId).findFirst()
                    .orElseThrow(() -> new IllegalStateException("You did not choose a first choice"));
            Integer rank = all.stream().filter(v -> v.getVoterId().equals(first) && v.getTargetMemberId().equals(buyer))
                    .map(Vote::getVoteRank).findFirst().orElse(null);
            myRank = new MyRank(person(context, first), rank, access.hasVoted(context.sessionId(), first));
        }
        if (product == ResultProduct.USER_RANKINGS || product == ResultProduct.BUNDLE) {
            rankings = List.of(ranking(context, target, all));
        } else if (product == ResultProduct.ALL_RANKINGS) {
            rankings = context.participants().stream().map(p -> ranking(context, p.memberId(), all)).toList();
        }
        return new Details(product, firstChoosers, myRank, rankings);
    }

    private Ranking ranking(LiveSessionContext context, Long memberId, List<Vote> all) {
        var choices = all.stream().filter(v -> v.getVoterId().equals(memberId))
                .map(v -> new RankedChoice(v.getVoteRank(), person(context, v.getTargetMemberId()))).toList();
        return new Ranking(person(context, memberId), access.hasVoted(context.sessionId(), memberId), choices);
    }

    private Person person(LiveSessionContext context, Long memberId) {
        return new Person(memberId, context.requireParticipant(memberId).alias());
    }

    public record ProductInfo(ResultProduct product, int priceGem, boolean targetRequired) { }
    public record Person(Long memberId, String alias) { }
    public record RankedChoice(int rank, Person target) { }
    public record Ranking(Person voter, boolean submitted, List<RankedChoice> choices) { }
    public record MyRank(Person myFirstChoice, Integer rankOfMe, boolean submitted) { }
    public record Details(ResultProduct product, List<Person> firstChoosers, MyRank myRank, List<Ranking> rankings) { }
    public record PurchaseResult(int chargedGem, boolean newPurchase, Details data) { }
}
