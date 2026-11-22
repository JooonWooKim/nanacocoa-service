package com.nanacocoa.server.recovery.dto.reqeust;

import jakarta.validation.constraints.*;

public final class RecoveryRequests {
    private RecoveryRequests() {}
    public record Request(@NotBlank @Email @Size(max = 255) String email) {
        @Override public String toString() { return "Request[redacted]"; }
    }
    public record Reset(@NotBlank @Size(max = 128) String token,
                        @NotBlank @Size(min = 8, max = 72) String newPassword,
                        @NotBlank @Size(min = 8, max = 72) String passwordConfirmation) {
        @Override public String toString() { return "Reset[redacted]"; }
    }
}
