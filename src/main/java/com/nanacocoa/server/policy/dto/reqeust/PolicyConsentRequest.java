package com.nanacocoa.server.policy.dto.reqeust;

import jakarta.validation.constraints.*;

public record PolicyConsentRequest(@NotBlank @Size(max = 64) String documentCode,
                                   @NotBlank @Size(max = 64) String version,
                                   @NotNull Boolean agreed) {}
