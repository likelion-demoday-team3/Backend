package com.example.kiwi_backend.member.application;
import com.example.kiwi_backend.contract.dto.ContactType;
import com.example.kiwi_backend.contract.query.MemberContactQuery;
import com.example.kiwi_backend.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberContactQueryImpl implements MemberContactQuery {
    private final MemberRepository members;
    public Map<ContactType, String> contacts(Long memberId) {
        var member = members.findById(memberId).orElse(null);
        if (member == null || !member.isActive()) return Map.of();
        Map<ContactType, String> values = new EnumMap<>(ContactType.class);
        for (ContactType type : ContactType.values()) {
            if (member.hasContact(type)) values.put(type, member.getContact(type));
        }
        return Map.copyOf(values);
    }
}
