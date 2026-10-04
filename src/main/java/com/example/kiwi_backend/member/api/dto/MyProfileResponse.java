package com.example.kiwi_backend.member.api.dto;

import com.example.kiwi_backend.member.domain.entity.Profile;

import java.util.List;

/** 본인 프로필 카드 (수정 화면용) */
public record MyProfileResponse(
        String university,
        String idealType,
        String datingStyle,
        String contactStyle,
        String loveStyle,
        List<String> interests
) {
    public static MyProfileResponse from(Profile profile) {
        return new MyProfileResponse(
                profile.getUniversity(),
                profile.getIdealType(),
                profile.getDatingStyle(),
                profile.getContactStyle(),
                profile.getLoveStyle(),
                profile.getInterestNames()
        );
    }
}
