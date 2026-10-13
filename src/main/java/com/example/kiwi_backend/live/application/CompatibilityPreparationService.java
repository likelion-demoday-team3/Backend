package com.example.kiwi_backend.live.application;

import com.example.kiwi_backend.contract.dto.PartnerProfileCard;
import com.example.kiwi_backend.contract.query.ProfileQueryService;
import com.example.kiwi_backend.live.domain.entity.LiveRoom;
import com.example.kiwi_backend.live.domain.entity.LiveRound;
import com.example.kiwi_backend.live.domain.entity.SessionCompatibility;
import com.example.kiwi_backend.live.domain.repository.LiveRoomRepository;
import com.example.kiwi_backend.live.domain.repository.LiveRoundRepository;
import com.example.kiwi_backend.live.domain.repository.SessionCompatibilityRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompatibilityPreparationService {

    private final RestClient.Builder restClientBuilder;
    private final SessionCompatibilityRepository compatibilityRepository;
    private final LiveRoundRepository roundRepository;
    private final LiveRoomRepository roomRepository;
    private final ProfileQueryService profileQueryService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    @Qualifier("compatibilityExecutor")
    private final Executor compatibilityExecutor;
    private final Set<Long> sessionsInProgress = ConcurrentHashMap.newKeySet();
    private final Map<Long, Instant> retryAfter = new ConcurrentHashMap<>();

    @Value("${app.liner.api-key:}")
    private String apiKey;

    @Value("${app.liner.model:liner-mark-1.3}")
    private String model;

    public boolean isPrepared(Long sessionId) {
        List<Pair> allPairs = sessionPairs(sessionId);
        if (allPairs.isEmpty()) return false;
        Set<String> saved = compatibilityRepository.findAllBySessionId(sessionId).stream()
                .map(result -> result.getMemberAId() + ":" + result.getMemberBId())
                .collect(java.util.stream.Collectors.toSet());
        return allPairs.stream().allMatch(pair -> saved.contains(pair.a() + ":" + pair.b()));
    }

    public void requestPreparation(Long sessionId) {
        Instant retryAt = retryAfter.get(sessionId);
        if (retryAt != null && clock.instant().isBefore(retryAt)) return;
        if (!sessionsInProgress.add(sessionId)) return;
        try {
            compatibilityExecutor.execute(() -> {
                try {
                    prepareMissing(sessionId);
                    retryAfter.remove(sessionId);
                } catch (RuntimeException e) {
                    retryAfter.put(sessionId, clock.instant().plus(Duration.ofSeconds(30)));
                    log.error("Compatibility preparation failed: sessionId={}", sessionId, e);
                } finally {
                    sessionsInProgress.remove(sessionId);
                }
            });
        } catch (RuntimeException e) {
            sessionsInProgress.remove(sessionId);
            retryAfter.put(sessionId, clock.instant().plus(Duration.ofSeconds(30)));
            throw e;
        }
    }

    /** Builds any missing session-pair results before the first round can start. */
    private void prepareMissing(Long sessionId) {
        List<Pair> allPairs = sessionPairs(sessionId);
        if (allPairs.isEmpty()) throw new IllegalStateException("궁합을 계산할 대화 상대가 없습니다");
        Set<String> saved = compatibilityRepository.findAllBySessionId(sessionId).stream()
                .map(result -> result.getMemberAId() + ":" + result.getMemberBId())
                .collect(java.util.stream.Collectors.toSet());
        List<Pair> missing = allPairs.stream()
                .filter(pair -> !saved.contains(pair.a() + ":" + pair.b()))
                .toList();
        if (missing.isEmpty()) {
            return;
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("LINER_API_KEY를 설정해야 세션을 시작할 수 있습니다");
        }

        Set<Long> memberIds = new HashSet<>();
        missing.forEach(pair -> { memberIds.add(pair.a()); memberIds.add(pair.b()); });
        Map<Long, PartnerProfileCard> cards = profileQueryService.getCards(memberIds);
        if (cards == null || memberIds.stream().anyMatch(id -> cards.get(id) == null)) {
            throw new IllegalStateException("궁합 계산에 필요한 프로필 카드가 없습니다");
        }
        List<Pair> indexedPairs = new ArrayList<>(missing);
        JsonNode response = restClientBuilder.build()
                .post()
                .uri("https://platform.liner.com/api/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .body(requestBody(indexedPairs, cards))
                .retrieve()
                .body(JsonNode.class);
        List<GeneratedResult> results = parseResults(response, indexedPairs.size());
        for (GeneratedResult result : results) {
            Pair pair = indexedPairs.get(result.index());
            compatibilityRepository.save(SessionCompatibility.create(
                    sessionId, pair.a(), pair.b(), result.score(), result.reason()));
        }
    }

    private List<Pair> sessionPairs(Long sessionId) {
        Map<String, Pair> pairs = new LinkedHashMap<>();
        List<LiveRound> rounds = roundRepository.findAllBySessionIdOrderByRoundNumberAsc(sessionId);
        for (LiveRound round : rounds) {
            for (LiveRoom room : roomRepository.findAllByRoundIdOrderByTableNoAsc(round.getId())) {
                Pair pair = new Pair(Math.min(room.getMaleMemberId(), room.getFemaleMemberId()),
                        Math.max(room.getMaleMemberId(), room.getFemaleMemberId()));
                pairs.putIfAbsent(pair.a() + ":" + pair.b(), pair);
            }
        }
        return List.copyOf(pairs.values());
    }

    private Map<String, Object> requestBody(List<Pair> pairs, Map<Long, PartnerProfileCard> cards) {
        List<Map<String, Object>> inputs = new ArrayList<>();
        for (int i = 0; i < pairs.size(); i++) {
            Pair pair = pairs.get(i);
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("index", i);
            input.put("profileA", profileFields(cards.get(pair.a())));
            input.put("profileB", profileFields(cards.get(pair.b())));
            inputs.add(input);
        }
        String profiles;
        try {
            profiles = objectMapper.writeValueAsString(inputs);
        } catch (Exception e) {
            throw new IllegalStateException("궁합 입력을 직렬화하지 못했습니다", e);
        }
        String prompt = "프로필 카드 두 개의 대화 궁합을 평가하세요. 외모, 성별, 나이, 연락처를 추측하거나 평가하지 마세요. "
                + "제공된 이상형, 데이트 스타일, 연락 스타일, 연애 성향, 관심사만 사용하세요. "
                + "score는 0~100 정수, reason은 한국어로 구체적이고 존중하는 한 문장으로 작성하세요. "
                + "입력의 모든 index에 대해 결과 하나씩 반환하고, JSON 형식으로만 답하세요. 입력: " + profiles;
        Map<String, Object> schema = Map.of(
                "name", "compatibility_results",
                "strict", true,
                "schema", Map.of(
                        "type", "object",
                        "properties", Map.of("results", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "index", Map.of("type", "integer"),
                                                "score", Map.of("type", "integer"),
                                                "reason", Map.of("type", "string")),
                                        "required", List.of("index", "score", "reason"),
                                        "additionalProperties", false))),
                        "required", List.of("results"),
                        "additionalProperties", false));
        Map<String, Object> format = Map.of("type", "json_schema", "json_schema", schema);
        return Map.of("model", model, "stream", false, "max_completion_tokens", 4000,
                "response_format", format,
                "messages", List.of(Map.of("role", "user", "content", prompt)));
    }

    private Map<String, Object> profileFields(PartnerProfileCard card) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("university", card.university());
        fields.put("idealType", card.idealType());
        fields.put("datingStyle", card.datingStyle());
        fields.put("contactStyle", card.contactStyle());
        fields.put("loveStyle", card.loveStyle());
        fields.put("interests", card.interests());
        return fields;
    }

    private List<GeneratedResult> parseResults(JsonNode response, int expectedCount) {
        JsonNode content = response == null ? null : response.path("choices").path(0).path("message").path("content");
        if (content == null || !content.isTextual()) {
            throw new IllegalStateException("Liner 응답에 궁합 결과가 없습니다");
        }
        try {
            JsonNode resultsNode = objectMapper.readTree(content.asText()).path("results");
            if (!resultsNode.isArray() || resultsNode.size() != expectedCount) {
                throw new IllegalStateException("Liner 응답의 궁합 결과 개수가 올바르지 않습니다");
            }
            Map<Integer, GeneratedResult> byIndex = new HashMap<>();
            for (JsonNode node : resultsNode) {
                int index = node.path("index").asInt(-1);
                String reason = node.path("reason").asText("").strip();
                int score = node.path("score").asInt(-1);
                if (index < 0 || index >= expectedCount || score < 0 || score > 100
                        || reason.isBlank() || reason.length() > 500
                        || byIndex.putIfAbsent(index, new GeneratedResult(index, score, reason)) != null) {
                    throw new IllegalStateException("Liner 응답에 유효하지 않은 궁합 결과가 있습니다");
                }
            }
            if (byIndex.size() != expectedCount) {
                throw new IllegalStateException("Liner 응답에서 일부 궁합 결과가 누락되었습니다");
            }
            return java.util.stream.IntStream.range(0, expectedCount).mapToObj(byIndex::get).toList();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Liner 궁합 응답을 해석하지 못했습니다", e);
        }
    }

    private record Pair(long a, long b) { }
    private record GeneratedResult(int index, int score, String reason) { }
}
