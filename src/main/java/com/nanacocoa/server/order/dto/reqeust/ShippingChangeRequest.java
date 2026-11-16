package com.nanacocoa.server.order.dto.reqeust;

import com.nanacocoa.server.order.entity.ShipmentEvent.Action;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record ShippingChangeRequest(@NotNull Action action, @NotNull UUID requestKey,
                                    @NotNull @PositiveOrZero Long revision,
                                    @Size(max = 32) String carrierCode,
                                    @Size(max = 64) String trackingNumber,
                                    @NotBlank @Size(max = 500) String reason,
                                    LocalDateTime shippedAt, LocalDateTime deliveredAt) {}
