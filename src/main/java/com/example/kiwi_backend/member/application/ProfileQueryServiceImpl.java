package com.example.kiwi_backend.member.application;

import com.example.kiwi_backend.member.domain.entity.Profile;
import com.example.kiwi_backend.member.domain.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/** A(live)가 라운드 배정 메시지에 상대 프로필 카드를 넣을 때 사용 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileQueryServiceImpl implements ProfileQueryService {

    private final ProfileRepository profileRepository;

    @Override
    public PartnerProfileCard getCard(Long memberId) {
        Profile profile = profileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new NotFoundException("프로필이 없습니다"));
        return toCard(memberId, profile);
    }

    @Override
    public Map<Long, PartnerProfileCard> getCards(Collection<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Map.of();
        }
        return profileRepository.findAllByMemberIdIn(memberIds).stream()
                .collect(Collectors.toMap(
                        profile -> profile.getMember().getId(),
                        profile -> toCard(profile.getMember().getId(), profile)
                ));
    }

    private PartnerProfileCard toCard(Long memberId, Profile profile) {
        return new PartnerProfileCard(
                memberId,
                profile.getUniversity(),
                profile.getIdealType(),
                profile.getDatingStyle(),
                profile.getContactStyle(),
                profile.getLoveStyle(),
                profile.getInterestNames()
        );
    }
}
