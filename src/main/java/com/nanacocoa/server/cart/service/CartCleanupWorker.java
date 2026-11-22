package com.nanacocoa.server.cart.service;
import com.nanacocoa.server.cart.repository.CartCheckoutRepository;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.*;
import org.springframework.data.domain.PageRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Component @RequiredArgsConstructor
public class CartCleanupWorker {
    private static final Logger log=LoggerFactory.getLogger(CartCleanupWorker.class);
    private final CartCleanupService cleanup;
    private final CartCheckoutRepository checkouts;
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void approved(CartCheckoutService.Approved event){attempt(event.orderId());}
    @Scheduled(fixedDelayString="${cart.cleanup.fixed-delay:30s}",initialDelayString="${cart.cleanup.initial-delay:30s}")
    public void retry(){for(Long id:checkouts.readyOrders(PageRequest.of(0,100)))attempt(id);}
    private void attempt(Long id){
        try{cleanup.clean(id);}catch(RuntimeException failure){log.warn("Cart cleanup deferred; order internal id={}",id);}
    }
}
