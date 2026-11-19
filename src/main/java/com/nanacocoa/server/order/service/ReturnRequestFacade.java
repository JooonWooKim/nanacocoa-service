package com.nanacocoa.server.order.service;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.order.dto.reqeust.CreateReturnRequest;
import com.nanacocoa.server.order.dto.reqeust.ReturnActionRequest;
import com.nanacocoa.server.order.dto.response.ReturnResponse;
import com.nanacocoa.server.order.entity.ReturnRequest;
import com.nanacocoa.server.payment.facade.PaymentFacade;
import com.nanacocoa.server.payment.service.PaymentTransactionalService;
import com.nanacocoa.server.payment.lock.OrderPaymentLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReturnRequestFacade {
    private final ReturnRequestService requests;
    private final PaymentTransactionalService transactions;
    private final PaymentFacade payments;
    private final OrderPaymentLock locks;

    public ReturnResponse.OrderView create(String orderId, CreateReturnRequest input, UserDetailsImpl user) {
        return requests.create(orderId, input, user);
    }
    public ReturnResponse.OrderView ownedView(String orderId, UserDetailsImpl user) { return requests.ownedView(orderId, user); }
    public ReturnResponse.Page list(String orderId, String type, String status, String page, UserDetailsImpl user) {
        return requests.list(orderId, type, status, page(page), user);
    }
    public ReturnResponse.Detail get(String id, UserDetailsImpl user) { return get(identifier(id), user); }
    public ReturnResponse.Detail action(String id, ReturnActionRequest input, UserDetailsImpl user) {
        return action(identifier(id), input, user);
    }
    public ReturnResponse.Detail get(long id, UserDetailsImpl user) { return requests.get(id, user); }
    public ReturnResponse.Detail action(long id, ReturnActionRequest input, UserDetailsImpl user) {
        if (input.action() != ReturnRequest.Action.REFUND) return requests.change(id, input, user);
        var target = requests.refundTarget(id, user);
        return locks.withOrderLock(target.orderId(), () -> {
            // PG 설정을 먼저 확인하고 준비 트랜잭션과 외부 호출을 분리한다.
            payments.requireProvider();
            var work = transactions.prepareReturnCancellation(target.paymentId(), id, input, user);
            if (!work.replay()) payments.executePreparedCancellation(work);
            return requests.get(id, user);
        });
    }
    private long identifier(String value) {
        try { long id = Long.parseLong(value); if (id > 0) return id; } catch (NumberFormatException ignored) {}
        throw new com.nanacocoa.server.common.exception.NanacocoaException(com.nanacocoa.server.common.exception.ErrorCode.INVALID_REQUEST);
    }
    private int page(String value) {
        try { int page = Integer.parseInt(value); if (page >= 0) return page; } catch (NumberFormatException ignored) {}
        throw new com.nanacocoa.server.common.exception.NanacocoaException(com.nanacocoa.server.common.exception.ErrorCode.INVALID_REQUEST);
    }
}
