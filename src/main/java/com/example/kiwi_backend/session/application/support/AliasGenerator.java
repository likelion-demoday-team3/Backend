package com.example.kiwi_backend.session.application.support;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.List;
import java.util.Set;

/**
 * 세션 안에서 겹치지 않는 별칭을 만든다. "졸린 수달" 같은 형식.
 *
 * 소개팅에서 사람에게 붙는 이름이라, 외모·체형을 연상시키는 단어나 부정적인 단어는 넣지 않는다.
 * 25 x 25 = 625가지라 한 세션(최대 12명)에서 모자랄 일은 없다.
 */
@Component
public class AliasGenerator {

    static final List<String> ADJECTIVES = List.of(
            "졸린", "수줍은", "용감한", "느긋한", "반짝이는",
            "포근한", "상냥한", "명랑한", "차분한", "다정한",
            "엉뚱한", "씩씩한", "수다스러운", "호기심 많은", "꼼꼼한",
            "낭만적인", "부지런한", "해맑은", "조용한", "산뜻한",
            "말랑한", "유쾌한", "신비로운", "따뜻한", "재빠른"
    );

    static final List<String> ANIMALS = List.of(
            "수달", "펭귄", "고양이", "강아지", "다람쥐",
            "토끼", "판다", "여우", "부엉이", "고래",
            "돌고래", "햄스터", "알파카", "코알라", "사슴",
            "오리", "해달", "미어캣", "쿼카", "너구리",
            "거북이", "참새", "고슴도치", "기린", "북극곰"
    );

    private static final int RANDOM_TRIES = 50;

    private final SecureRandom random = new SecureRandom();

    /** usedAliases에 없는 별칭을 하나 만든다 */
    public String generate(Set<String> usedAliases) {
        for (int i = 0; i < RANDOM_TRIES; i++) {
            String candidate = compose(
                    ADJECTIVES.get(random.nextInt(ADJECTIVES.size())),
                    ANIMALS.get(random.nextInt(ANIMALS.size())));
            if (!usedAliases.contains(candidate)) {
                return candidate;
            }
        }
        // 랜덤으로 계속 겹치는 극단적인 경우: 남은 조합을 순서대로 찾는다
        for (String adjective : ADJECTIVES) {
            for (String animal : ANIMALS) {
                String candidate = compose(adjective, animal);
                if (!usedAliases.contains(candidate)) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("사용 가능한 별칭이 없습니다");
    }

    private static String compose(String adjective, String animal) {
        return adjective + " " + animal;
    }
}
