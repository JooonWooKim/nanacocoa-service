package com.nanacocoa.server.member.dto.response;

import com.nanacocoa.server.member.entity.EmailReceiveConsent;
import com.nanacocoa.server.member.entity.Member;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MemberProfileResponse {
    private final String email;
    private final String name;
    private final String address;
    private final String phoneNumber;
    private final EmailReceiveConsent emailReceiveConsent;

    public static MemberProfileResponse from(Member member) {
        return new MemberProfileResponse(
                member.getEmail(),
                member.getName(),
                member.getAddress(),
                member.getPhoneNumber(),
                member.getEmailReceiveConsent()
        );
    }
}
