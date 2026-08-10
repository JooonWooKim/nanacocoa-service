package com.nanacocoa.server.member.dto.response;

import com.nanacocoa.server.member.entity.Member;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthSessionResponse {
	private final boolean authenticated;
	private final String email;
	private final String name;
	private final boolean admin;

	public static AuthSessionResponse anonymous() {
		return new AuthSessionResponse(false, null, null, false);
	}

	public static AuthSessionResponse from(Member member) {
		return new AuthSessionResponse(true, member.getEmail(), member.getName(), member.isAdmin());
	}
}
