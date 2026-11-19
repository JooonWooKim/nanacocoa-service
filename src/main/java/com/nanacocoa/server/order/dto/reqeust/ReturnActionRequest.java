package com.nanacocoa.server.order.dto.reqeust;
import com.nanacocoa.server.order.entity.ReturnRequest;
import jakarta.validation.constraints.*;
import java.util.UUID;

public record ReturnActionRequest(@NotNull UUID requestKey, @NotNull @Min(0) Long revision,
    @NotNull ReturnRequest.Action action, @NotBlank @Size(max = 500) String reason,
    Boolean resellable, Boolean refundEligible) {}
