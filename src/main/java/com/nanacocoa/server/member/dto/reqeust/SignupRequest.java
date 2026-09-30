package com.nanacocoa.server.member.dto.reqeust;

import com.nanacocoa.server.member.entity.EmailReceiveConsent;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class SignupRequest {

	@NotBlank
	@Email
	@Size(max = 255)
	private String email;

	@NotBlank
	@Size(min = 8, max = 72)
	private String password;

	@NotBlank
	@Size(max = 100)
	private String name;

	@NotBlank
	@Size(max = 500)
	private String address;

	@NotBlank
	@Pattern(regexp = "^010-\\d{4}-\\d{4}$")
	private String phoneNumber;

	@NotNull
	private EmailReceiveConsent emailReceiveConsent;
}
