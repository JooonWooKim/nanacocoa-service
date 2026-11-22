package com.nanacocoa.server.order.dto.reqeust;

import jakarta.validation.Valid;
import com.nanacocoa.server.cart.dto.reqeust.CartRequests.Selection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
	@NotBlank
	@Size(max = 100)
	private String recipientName;

	@NotBlank
	@Size(max = 30)
	private String phoneNumber;

	@NotBlank
	@Size(max = 500)
	private String shippingAddress;

	@Size(max = 500)
	private String customerRequest;

	@NotEmpty
	private List<@Valid OrderItemRequest> items;
    @Valid
    private List<@jakarta.validation.constraints.NotNull @Valid Selection> cartItems;

    public CreateOrderRequest(String recipientName,String phoneNumber,String shippingAddress,String customerRequest,List<OrderItemRequest> items){
        this(recipientName,phoneNumber,shippingAddress,customerRequest,items,null);
    }
}
