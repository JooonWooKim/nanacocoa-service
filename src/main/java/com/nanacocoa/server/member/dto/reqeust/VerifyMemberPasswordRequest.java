package com.nanacocoa.server.member.dto.reqeust;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class VerifyMemberPasswordRequest {
    @NotBlank
    @Size(max = 72)
    private String currentPassword;
}
