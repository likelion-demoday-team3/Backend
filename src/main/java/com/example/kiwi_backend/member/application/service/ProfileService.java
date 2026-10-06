package com.example.kiwi_backend.member.application.service;

import com.example.kiwi_backend.member.api.dto.MyProfileResponse;
import com.example.kiwi_backend.member.api.dto.ProfileUpdateRequest;
import com.example.kiwi_backend.common.exception.NotFoundException;
import com.example.kiwi_backend.member.application.ProfileTextFilter;
import com.example.kiwi_backend.member.domain.entity.Profile;
import com.example.kiwi_backend.member.domain.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 본인 프로필 카드 조회·수정. 프로필은 가입할 때 빈 상태로 만들어진다 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final ProfileTextFilter textFilter;

    @Transactional(readOnly = true)
    public MyProfileResponse getMyProfile(Long memberId) {
        return MyProfileResponse.from(findProfile(memberId));
    }

    public MyProfileResponse updateProfile(Long memberId, ProfileUpdateRequest request) {
        textFilter.check("이상형", request.idealType());
        textFilter.check("데이트 스타일", request.datingStyle());
        textFilter.check("연락 빈도", request.contactStyle());
        textFilter.check("연애 성향", request.loveStyle());
        List<String> interests = request.interests() == null ? List.of() : request.interests();
        interests.forEach(interest -> textFilter.check("관심사", interest));

        Profile profile = findProfile(memberId);
        profile.update(request.idealType(), request.datingStyle(), request.contactStyle(), request.loveStyle());
        profile.replaceInterests(interests);
        return MyProfileResponse.from(profile);
    }

    private Profile findProfile(Long memberId) {
        return profileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new NotFoundException("프로필이 없습니다"));
    }
}

