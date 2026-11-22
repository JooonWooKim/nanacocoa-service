package com.nanacocoa.server.cart.service;
import com.nanacocoa.server.cart.entity.*;
import com.nanacocoa.server.cart.repository.*;
import com.nanacocoa.server.cart.dto.reqeust.CartRequests.Selection;
import com.nanacocoa.server.order.dto.reqeust.CreateOrderRequest;
import com.nanacocoa.server.order.entity.Order;
import com.nanacocoa.server.products.entity.Products;
import com.nanacocoa.server.common.exception.NanacocoaException;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.*;
import lombok.RequiredArgsConstructor;
import java.util.*;
import static com.nanacocoa.server.common.exception.ErrorCode.*;
@Service @RequiredArgsConstructor @Transactional(propagation=Propagation.MANDATORY)
public class CartCheckoutService {
    private final CartItemRepository items;
    private final CartCheckoutRepository checkouts;
    private final ApplicationEventPublisher events;
    public List<CartCheckout.Snapshot> validate(Long member,CreateOrderRequest request,Map<Long,Products> products){
        if(request.getCartItems()==null)return List.of();
        var selected=request.getCartItems();
        if(selected.isEmpty() || selected.size()!=request.getItems().size())throw new NanacocoaException(INVALID_ORDER_ITEMS);
        if(selected.stream().anyMatch(java.util.Objects::isNull))throw new NanacocoaException(INVALID_ORDER_ITEMS);
        var result=new ArrayList<CartCheckout.Snapshot>();var ids=new HashSet<Long>();var productIds=new HashSet<Long>();
        for(var s:selected.stream().sorted(Comparator.comparing(Selection::itemId,Comparator.nullsFirst(Comparator.naturalOrder()))).toList()){
            if(s==null || s.itemId()==null || s.expectedVersion()==null || s.quantity()==null || s.quantity()<=0 || s.expectedUnitPrice()==null || !ids.add(s.itemId()))throw new NanacocoaException(INVALID_ORDER_ITEMS);
            var item=items.ownedForUpdate(member,s.itemId()).orElseThrow(()->new NanacocoaException(CART_NOT_FOUND));item.expect(s.expectedVersion());
            var p=products.get(item.getProductId());
            if(p==null || !productIds.add(p.getId()) || item.getQuantity()<s.quantity() || !p.getPrice().equals(s.expectedUnitPrice()))throw new NanacocoaException(CART_CONFLICT);
            if(request.getItems().stream().noneMatch(i->i.getProductId().equals(p.getId()) && i.getQuantity().equals(s.quantity())))throw new NanacocoaException(INVALID_ORDER_ITEMS);
            result.add(new CartCheckout.Snapshot(item.getId(),item.getVersion(),p.getId(),s.quantity(),p.getPrice()));
        }
        return result;
    }
    public void attach(Order order,List<CartCheckout.Snapshot> snapshots){if(!snapshots.isEmpty())checkouts.save(new CartCheckout(order.getId(),order.getMember().getId(),snapshots));}
    public boolean matches(Order order,CreateOrderRequest request){
        var saved=checkouts.findByOrderId(order.getId());
        if(saved.isEmpty())return request.getCartItems()==null;
        if(request.getCartItems()==null || request.getCartItems().size()!=saved.get().getItems().size())return false;
        var ids=new HashSet<Long>();
        return request.getCartItems().stream().allMatch(s->s!=null && ids.add(s.itemId()) && saved.get().getItems().stream().anyMatch(v->v.getItemId().equals(s.itemId()) && v.getItemVersion().equals(s.expectedVersion()) && Objects.equals(v.getQuantity(),s.quantity()) && v.getUnitPrice().equals(s.expectedUnitPrice())));
    }
    public void approved(Order order){
        checkouts.findByOrderId(order.getId()).ifPresent(c->{c.ready();events.publishEvent(new Approved(order.getId()));});
    }
    public record Approved(Long orderId){}
}
