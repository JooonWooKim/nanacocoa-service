package com.nanacocoa.server.cart.dto.response;
import java.util.List;
public record CartResponse(List<Item> items,long totalQuantity) {
    public record Item(Long itemId,Long version,Long productId,String name,String imageUrl,long unitPrice,int quantity,String availability){}
}
