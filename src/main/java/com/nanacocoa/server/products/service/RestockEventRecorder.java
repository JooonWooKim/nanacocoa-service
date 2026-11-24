package com.nanacocoa.server.products.service;

import com.nanacocoa.server.products.entity.ProductRestockEvent;
import com.nanacocoa.server.products.entity.Products;
import com.nanacocoa.server.products.repository.ProductRestockEventRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RestockEventRecorder {
    private final ProductRestockEventRepository events;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordIfAvailable(Products product, long previousStock) {
        if (previousStock == 0 && product.isPurchasable()) {
            events.save(new ProductRestockEvent(product.getId(), product.getAvailabilityEpoch(),
                    LocalDateTime.now(ZoneOffset.UTC)));
        }
    }
}
