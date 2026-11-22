package com.nanacocoa.server.cart.service;
import com.nanacocoa.server.cart.repository.*;
import com.nanacocoa.server.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor
public class CartCleanupService {
    private final CartItemRepository items;
    private final CartCheckoutRepository checkouts;
    private final OrderRepository orders;
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void clean(Long orderId){
        if(orders.findByIdForCartUpdate(orderId).isEmpty())return;
        var work=checkouts.orderForUpdate(orderId).orElse(null);
        if(work==null || !work.getState().equals("READY"))return;
        for(var snapshot:work.getItems()){
            var item=items.ownedForUpdate(work.getMemberId(),snapshot.getItemId()).orElse(null);
            if(item==null || !item.getVersion().equals(snapshot.getItemVersion())){snapshot.outcome("PRESERVED");continue;}
            if(item.getQuantity()<snapshot.getQuantity())throw new IllegalStateException("Invalid cart snapshot quantity");
            if(item.getQuantity()==snapshot.getQuantity())items.delete(item);else item.quantity(item.getQuantity()-snapshot.getQuantity());
            snapshot.outcome("REMOVED");
        }
        work.done();items.flush();checkouts.flush();
    }
}
