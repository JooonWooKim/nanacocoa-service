package com.nanacocoa.server.member.dto.reqeust;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChangeMemberPasswordRequest {
    @NotBlank
    @Size(max = 72)
    private String currentPassword;

    @NotBlank
    @Size(min = 8, max = 72)
    private String newPassword;

    @NotBlank
    @Size(min = 8, max = 72)
    private String newPasswordConfirm;
}
