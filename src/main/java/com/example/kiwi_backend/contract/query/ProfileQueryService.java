package com.example.kiwi_backend.contract.query;

import com.example.kiwi_backend.contract.dto.PartnerProfileCard;

import java.util.Collection;
import java.util.Map;

public interface ProfileQueryService {

    PartnerProfileCard getCard(Long memberId);

    Map<Long, PartnerProfileCard> getCards(Collection<Long> memberIds);
}
