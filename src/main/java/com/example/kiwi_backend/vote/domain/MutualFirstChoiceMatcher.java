package com.example.kiwi_backend.vote.domain;
import com.example.kiwi_backend.contract.dto.MatchedPair;
import java.util.*;
public final class MutualFirstChoiceMatcher {
    private MutualFirstChoiceMatcher() { }
    public static List<MatchedPair> match(Map<Long, Long> firstChoices) {
        List<MatchedPair> result = new ArrayList<>();
        for (var entry : firstChoices.entrySet()) {
            Long a = entry.getKey();
            Long b = entry.getValue();
            if (b != null && a < b && a.equals(firstChoices.get(b))) result.add(new MatchedPair(a, b));
        }
        result.sort(Comparator.comparing(MatchedPair::memberAId));
        return List.copyOf(result);
    }
}
