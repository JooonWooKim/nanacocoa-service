package com.nanacocoa.server.member.dto.reqeust;

import com.nanacocoa.server.member.entity.Member;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class SignupRequest {

		private String email;
		private String password;
		private String name;

	public Member toMember(SignupRequest request, String encodePassword) {
		return Member.builder()
					   .email(request.getEmail())
					   .name(request.getName())
					   .password(encodePassword)
					   .build();
	}
}
