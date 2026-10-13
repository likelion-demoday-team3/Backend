package com.example.kiwi_backend.contract.query;
import com.example.kiwi_backend.contract.dto.ContactType;
import java.util.Map;
/** Internal only. Callers must check a persisted sharing grant before exposing a value. */
public interface MemberContactQuery {
    Map<ContactType, String> contacts(Long memberId);
}
