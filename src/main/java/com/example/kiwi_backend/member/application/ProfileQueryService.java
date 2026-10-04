package com.example.kiwi_backend.member.application;

import java.util.Collection;
import java.util.Map;

public interface ProfileQueryService {

    PartnerProfileCard getCard(Long memberId);

    Map<Long, PartnerProfileCard> getCards(Collection<Long> memberIds);
}
