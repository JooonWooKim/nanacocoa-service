package com.nanacocoa.server.products.dto.reqeust;

import com.nanacocoa.server.products.entity.SaleStatus;
import jakarta.validation.constraints.*;

public record UpdateInventoryRequest(
	@NotNull @Min(0) @Max(1000000000L) Long stockQuantity,
	@NotNull SaleStatus saleStatus,
	@NotNull @Min(0) Long expectedVersion) { }
