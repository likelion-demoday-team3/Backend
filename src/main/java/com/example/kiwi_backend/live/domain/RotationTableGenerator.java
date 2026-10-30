package com.example.kiwi_backend.live.domain;

import com.example.kiwi_backend.contract.dto.Gender;
import com.example.kiwi_backend.contract.dto.LiveParticipant;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class RotationTableGenerator {
// 남+녀를 합쳐서 최소 5명, 남녀 차이 최대 1명
    private static final int MIN_PARTICIPANT_COUNT = 5;
    private static final int MAX_GENDER_COUNT_DIFFERENCE = 1;

    public RotationTable generate(Long sessionId, List<LiveParticipant> participants) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        if (participants == null) {
            throw new IllegalArgumentException("participants must not be null");
        }
        validateMinimumParticipantCount(participants);
        validateDuplicateMembers(participants);

        List<LiveParticipant> males = sortedByMemberId(participants, Gender.MALE);
        List<LiveParticipant> females = sortedByMemberId(participants, Gender.FEMALE);

        if (males.isEmpty() || females.isEmpty()) {
            throw new IllegalArgumentException("both male and female participants are required");
        }
        validateGenderBalance(males, females);

        int roundCount = Math.max(males.size(), females.size());
        List<LiveParticipant> maleSlots = paddedSlots(males, roundCount);
        List<LiveParticipant> femaleSlots = paddedSlots(females, roundCount);
        List<RoundAssignment> rounds = new ArrayList<>();

        for (int roundIndex = 0; roundIndex < roundCount; roundIndex++) {
            List<MatchPair> pairs = new ArrayList<>();
            Set<Long> matchedMemberIds = new HashSet<>();
//            roundIndex는 라운드 번호

            for (int slotIndex = 0; slotIndex < roundCount; slotIndex++) {
                LiveParticipant male = maleSlots.get(slotIndex);
                LiveParticipant female = femaleSlots.get((slotIndex + roundIndex) % roundCount);
//            slotIndex는 * 라운드 안의 자리 번호

                if (male == null || female == null) {
                    continue;
                }
//             둘 중 하나가 없으면 매칭 만들지 말고 넘어가라

                pairs.add(new MatchPair(pairs.size() + 1, male.memberId(), female.memberId()));
                matchedMemberIds.add(male.memberId());
                matchedMemberIds.add(female.memberId());
            }

            List<Long> idleMemberIds = participants.stream()
                    .map(LiveParticipant::memberId)
                    .filter(memberId -> !matchedMemberIds.contains(memberId))
                    .sorted()
                    .toList();

            rounds.add(new RoundAssignment(roundIndex + 1, List.copyOf(pairs), idleMemberIds));
        }

        return new RotationTable(sessionId, List.copyOf(rounds));
    }

    private void validateMinimumParticipantCount(List<LiveParticipant> participants) {
        if (participants.size() < MIN_PARTICIPANT_COUNT) {
            throw new IllegalArgumentException("at least 5 participants are required");
        }
    }

    private void validateDuplicateMembers(List<LiveParticipant> participants) {
        Set<Long> memberIds = new HashSet<>();
        for (LiveParticipant participant : participants) {
            if (!memberIds.add(participant.memberId())) {
                throw new IllegalArgumentException("duplicate participants are not allowed");
            }
        }
//        같은 회원이 중복으로 들어오는 걸 막는 코드
    }

    private void validateGenderBalance(List<LiveParticipant> males, List<LiveParticipant> females) {
        if (Math.abs(males.size() - females.size()) > MAX_GENDER_COUNT_DIFFERENCE) {
            throw new IllegalArgumentException("gender count difference must be 1 or less");
        }
    }

    private List<LiveParticipant> sortedByMemberId(List<LiveParticipant> participants, Gender gender) {
        return participants.stream()
                .filter(participant -> participant.gender() == gender)
                .sorted(Comparator.comparing(LiveParticipant::memberId))
                .toList();
    }

    private List<LiveParticipant> paddedSlots(List<LiveParticipant> participants, int size) {
        List<LiveParticipant> slots = new ArrayList<>(participants);
        while (slots.size() < size) {
            slots.add(null);
        }
        return slots;
    }
}
