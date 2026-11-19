package com.nanacocoa.server.order.dto.reqeust;
import com.nanacocoa.server.order.entity.ReturnRequest;
import jakarta.validation.constraints.*;
import java.util.UUID;

public record CreateReturnRequest(@NotNull UUID requestKey, @NotNull ReturnRequest.Type type,
    @NotBlank @Size(max = 500) String reason) {}
