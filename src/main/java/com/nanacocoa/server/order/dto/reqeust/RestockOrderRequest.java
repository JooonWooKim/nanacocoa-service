package com.nanacocoa.server.order.dto.reqeust;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RestockOrderRequest(@NotBlank @Size(max = 500) String reason) { }
