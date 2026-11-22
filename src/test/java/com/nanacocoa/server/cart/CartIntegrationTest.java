package com.nanacocoa.server.cart;

import com.nanacocoa.server.cart.dto.reqeust.CartRequests.*;
import com.nanacocoa.server.cart.dto.response.CartResponse;
import com.nanacocoa.server.cart.repository.*;
import com.nanacocoa.server.cart.service.*;
import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.order.dto.reqeust.*;
import com.nanacocoa.server.order.dto.response.OrderResponse;
import com.nanacocoa.server.order.repository.OrderRepository;
import com.nanacocoa.server.order.service.OrderService;
import com.nanacocoa.server.payment.dto.reqeust.ConfirmPaymentRequest;
import com.nanacocoa.server.payment.pg.*;
import com.nanacocoa.server.payment.service.*;
import com.nanacocoa.server.products.entity.*;
import com.nanacocoa.server.products.repository.ProductsRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.util.*;
import java.util.concurrent.*;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.nanacocoa.server.CsrfRequests.csrf;

@ActiveProfiles("test") @AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@SpringBootTest(properties={"spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=validate","spring.jpa.open-in-view=false","spring.sql.init.mode=never","cart.cleanup.initial-delay=1h"})
class CartIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r){
        String url=System.getenv("CART_TEST_DB_URL");
        r.add("spring.datasource.url",()->url==null?"jdbc:h2:mem:cart;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000":url);
        r.add("spring.datasource.driver-class-name",()->url==null?"org.h2.Driver":"com.mysql.cj.jdbc.Driver");
        r.add("spring.datasource.username",()->url==null?"sa":"root");r.add("spring.datasource.password",()->"");
    }
    @Autowired CartService cart; @Autowired OrderService orders; @Autowired MemberRepository members;
    @Autowired ProductsRepository products; @Autowired OrderRepository orderRepository;
    @Autowired PaymentTransactionalService payments; @Autowired PaymentRecoveryService recovery;
    @Autowired CartCleanupWorker worker; @Autowired CartCleanupService cleanup;
    @MockitoSpyBean CartItemRepository items;
    @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc;
    @MockitoBean TossPaymentsClient pg;
    UserDetailsImpl buyer;
    @BeforeEach void setup(){buyer=identity();}
    UserDetailsImpl identity(){var m=members.saveAndFlush(Member.builder().email(UUID.randomUUID()+"@example.test").name("가상 회원").password("encoded").build());return new UserDetailsImpl(m,m.getEmail());}
    Products product(){var p=Products.builder().name("cart-"+UUID.randomUUID()).price(41000L).summary("요약").detailTitle("상세").description("설명").build();p.updateInventory(20,SaleStatus.ON_SALE,0);return products.saveAndFlush(p);}
    String key(){return UUID.randomUUID().toString();}
    CartResponse.Item add(Products p,int q){return cart.add(new Add(p.getId(),q),key(),buyer).items().stream().filter(i->i.productId().equals(p.getId())).findFirst().orElseThrow();}
    CreateOrderRequest request(List<CartResponse.Item> rows){return new CreateOrderRequest("가상 수령인","010-0000-0000","가상 배송지",null,rows.stream().map(i->new OrderItemRequest(i.productId(),i.quantity())).toList(),rows.stream().map(i->new Selection(i.itemId(),i.version(),i.quantity(),i.unitPrice())).toList());}
    OrderResponse order(List<CartResponse.Item> rows){return orders.createOrder(request(rows),key(),null,buyer).order();}
    PaymentTransactionalService.ApprovalWork prepare(OrderResponse order){return payments.prepareApproval(new ConfirmPaymentRequest(key(),order.getOrderId(),order.getTotalAmount()),key(),key(),buyer.getUsername());}
    TossPaymentResponse response(PaymentTransactionalService.ApprovalWork w){return new TossPaymentResponse(w.paymentKey(),w.orderId(),w.amount(),"DONE","카드",OffsetDateTime.now(),List.of());}
    MockHttpSession session(UserDetailsImpl who){var c=SecurityContextHolder.createEmptyContext();c.setAuthentication(new UsernamePasswordAuthenticationToken(who,null,who.getAuthorities()));var s=new MockHttpSession();s.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,c);return s;}

    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder csrf(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,MockHttpSession session){return request.session(session).with(com.nanacocoa.server.CsrfRequests.csrf(mvc));}

    @Test void cartPersistsAndAddReplayDoesNotResurrectDeletedItem(){
        var p=product();String k=key();cart.add(new Add(p.getId(),2),k,buyer);var row=cart.add(new Add(p.getId(),2),k,buyer).items().get(0);
        assertThat(row.quantity()).isEqualTo(2);assertThat(cart.get(new UserDetailsImpl(members.findByEmail(buyer.getUsername()).orElseThrow(),buyer.getUsername())).totalQuantity()).isEqualTo(2);
        cart.delete(row.itemId(),row.version(),buyer);assertThat(cart.add(new Add(p.getId(),2),k,buyer).items()).isEmpty();
        assertThatThrownBy(()->cart.add(new Add(p.getId(),3),k,buyer)).isInstanceOf(NanacocoaException.class);
        assertThat(products.findById(p.getId()).orElseThrow().getReservedQuantity()).isZero();
    }
    @Test void versionConflictAndDeleteReinsertProtectNewItems(){
        var p=product();var row=add(p,2);var updated=cart.update(row.itemId(),new Update(3,row.version()),buyer).items().get(0);
        assertThatThrownBy(()->cart.update(row.itemId(),new Update(4,row.version()),buyer)).isInstanceOf(NanacocoaException.class);
        cart.delete(updated.itemId(),updated.version(),buyer);assertThat(add(p,1).itemId()).isNotEqualTo(row.itemId());
    }
    @Test void selectedMultiOrderReplaysAfterApprovalAndLeavesUnselected(){
        var a=add(product(),2);var b=add(product(),1);var unselected=add(product(),3);var req=request(List.of(a,b));String k=key();
        var order=orders.createOrder(req,k,null,buyer).order();assertThat(cart.get(buyer).items()).hasSize(3);
        var work=prepare(order);payments.completeApproval(work.paymentId(),response(work));
        assertThat(cart.get(buyer).items()).extracting(CartResponse.Item::itemId).containsExactly(unselected.itemId());
        assertThat(orders.createOrder(req,k,null,buyer).order().getOrderId()).isEqualTo(order.getOrderId());
        payments.completeApproval(work.paymentId(),response(work));worker.retry();
        assertThat(cart.get(buyer).totalQuantity()).isEqualTo(3);verifyNoInteractions(pg);
    }
    @Test void changedAndReinsertedRowsArePreservedOnApproval(){
        var p=product();var a=add(p,2);var q=product();var b=add(q,1);var order=order(List.of(a,b));
        add(p,1);cart.delete(b.itemId(),b.version(),buyer);var replacement=add(q,4);
        var work=prepare(order);payments.completeApproval(work.paymentId(),response(work));worker.retry();
        assertThat(cart.get(buyer).totalQuantity()).isEqualTo(7);
        assertThat(cart.get(buyer).items()).extracting(CartResponse.Item::itemId).contains(replacement.itemId());
        assertThat(jdbc.queryForObject("select count(*) from cart_checkout_items where checkout_id=(select id from cart_checkouts where order_id=?) and outcome='PRESERVED'",Integer.class,orderRepository.findByOrderId(order.getOrderId()).orElseThrow().getId())).isEqualTo(2);
    }
    @Test void failedApprovalAndCanceledUnpaidOrderKeepCart(){
        var row=add(product(),1);var order=order(List.of(row));var work=prepare(order);payments.failApproval(work.paymentId(),"DECLINED","합성 거절");worker.retry();
        assertThat(cart.get(buyer).totalQuantity()).isEqualTo(1);orders.cancelUnpaid(order.getOrderId(),buyer);assertThat(cart.get(buyer).totalQuantity()).isEqualTo(1);
        verifyNoInteractions(pg);
    }
    @Test void uncertainApprovalRecoveryCleansWithoutBrowserOrSecondConfirm(){
        var row=add(product(),1);var order=order(List.of(row));var work=prepare(order);
        payments.recordUncertainApproval(work.paymentId(),"TIMEOUT","합성 지연");assertThat(cart.get(buyer).totalQuantity()).isEqualTo(1);
        jdbc.update("update payments set next_retry_at=? where id=?",java.time.LocalDateTime.now().minusSeconds(1),work.paymentId());
        when(pg.findByOrderId(work.orderId())).thenReturn(response(work));recovery.reconcileDueApprovals(100);
        assertThat(cart.get(buyer).items()).isEmpty();verify(pg,times(1)).findByOrderId(work.orderId());verify(pg,never()).confirm(anyString(),anyString(),anyLong(),anyString());
    }
    @Test void cleanupFailureRollsBackAllRowsWithoutCompensatingPayment(){
        var a=add(product(),1);var b=add(product(),2);var order=order(List.of(a,b));var work=prepare(order);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic cleanup failure")).doNothing().when(items).flush();
        payments.completeApproval(work.paymentId(),response(work));
        assertThat(cart.get(buyer).totalQuantity()).isEqualTo(3);
        assertThat(jdbc.queryForObject("select status from payments where id=?",String.class,work.paymentId())).isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("select state from cart_checkouts where order_id=?",String.class,orderRepository.findByOrderId(order.getOrderId()).orElseThrow().getId())).isEqualTo("READY");
        worker.retry();worker.retry();assertThat(cart.get(buyer).items()).isEmpty();verifyNoInteractions(pg);
    }
    @Test void priceAndStockConflictsNeverCreatePartialOrders(){
        var p=product();var a=add(p,1);var b=add(product(),2);var req=request(List.of(a,b));
        jdbc.update("update products set price=42000 where id=?",p.getId());
        long before=orderRepository.count();assertThatThrownBy(()->orders.createOrder(req,key(),null,buyer)).isInstanceOf(NanacocoaException.class);assertThat(orderRepository.count()).isEqualTo(before);
        assertThat(products.findById(b.productId()).orElseThrow().getReservedQuantity()).isZero();
        jdbc.update("update products set price=41000, stock_quantity=0 where id=?",p.getId());
        assertThatThrownBy(()->orders.createOrder(req,key(),null,buyer)).isInstanceOf(NanacocoaException.class);assertThat(orderRepository.count()).isEqualTo(before);
    }
    @Test void concurrentAddReplayIsAppliedOnceAndConcurrentUpdatesConflict()throws Exception{
        var p=product();String k=key();var pool=Executors.newFixedThreadPool(2);
        try{
            var futures=pool.invokeAll(List.of(()->cart.add(new Add(p.getId(),2),k,buyer),()->cart.add(new Add(p.getId(),2),k,buyer)));for(var f:futures)f.get();
            var row=cart.get(buyer).items().get(0);assertThat(row.quantity()).isEqualTo(2);
            var tasks=new ArrayList<Callable<Boolean>>();for(int n:List.of(3,4))tasks.add(()->{try{cart.update(row.itemId(),new Update(n,row.version()),buyer);return true;}catch(NanacocoaException conflict){return false;}});
            assertThat(pool.invokeAll(tasks).stream().map(f->{try{return f.get();}catch(Exception e){throw new RuntimeException(e);}}).toList()).containsExactlyInAnyOrder(true,false);
        }finally{pool.shutdownNow();}
    }
    @Test void directPurchaseDoesNotCleanCart(){
        var row=add(product(),2);var req=new CreateOrderRequest("가상 수령인","010-0000-0000","가상 주소",null,List.of(new OrderItemRequest(row.productId(),1)));
        var order=orders.createOrder(req,key(),null,buyer).order();var work=prepare(order);payments.completeApproval(work.paymentId(),response(work));assertThat(cart.get(buyer).totalQuantity()).isEqualTo(2);
    }
    @Test void manualRecoveryAndPartialCartQuantityAreCleanedOnce(){
        var row=add(product(),3);
        var req=new CreateOrderRequest("가상 수령인","010-0000-0000","가상 주소",null,List.of(new OrderItemRequest(row.productId(),1)),List.of(new Selection(row.itemId(),row.version(),1,row.unitPrice())));
        var order=orders.createOrder(req,key(),null,buyer).order();var work=prepare(order);
        payments.recordUncertainApproval(work.paymentId(),"TIMEOUT","합성 지연");
        jdbc.update("update payments set next_retry_at=? where id=?",java.time.LocalDateTime.now().minusSeconds(1),work.paymentId());
        when(pg.findByOrderId(work.orderId())).thenReturn(response(work));recovery.reconcileOneApproval(work.paymentId(),work.orderId());
        cleanup.clean(orderRepository.findByOrderId(order.getOrderId()).orElseThrow().getId());
        assertThat(cart.get(buyer).totalQuantity()).isEqualTo(2);
        assertThat(cart.get(buyer).items().get(0).version()).isGreaterThan(row.version());
    }
    @Test void overflowAndMismatchedOrForeignSelectionAreRejectedWithoutWrites(){
        var p=product();var row=add(p,Integer.MAX_VALUE);
        assertThatThrownBy(()->cart.add(new Add(p.getId(),1),key(),buyer)).isInstanceOf(NanacocoaException.class);
        assertThat(cart.get(buyer).items().get(0).quantity()).isEqualTo(Integer.MAX_VALUE);
        var req=request(List.of(row));assertThatThrownBy(()->orders.createOrder(req,key(),null,identity())).isInstanceOf(NanacocoaException.class);
        var mismatch=new CreateOrderRequest("가상 수령인","010-0000-0000","가상 주소",null,List.of(new OrderItemRequest(p.getId(),1)),req.getCartItems());
        assertThatThrownBy(()->orders.createOrder(mismatch,key(),null,buyer)).isInstanceOf(NanacocoaException.class);
        jdbc.update("update products set price=? where id=?",Long.MAX_VALUE,p.getId());
        assertThatThrownBy(()->cart.update(row.itemId(),new Update(2,row.version()),buyer)).isInstanceOf(NanacocoaException.class);
    }

    @Test void cartHttpRequiresOwnerCsrfAndStrictIntegerQuantity()throws Exception{
        var row=add(product(),2);var own=session(buyer);var other=session(identity());
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/cart").session(own)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.data.totalQuantity").value(2)).andExpect(jsonPath("$.data.items[0].stockQuantity").doesNotExist());
        mvc.perform(put("/api/cart/items/"+row.itemId()).session(own).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":3,\"expectedVersion\":0}")).andExpect(status().isForbidden());
        mvc.perform(csrf(delete("/api/cart/items/"+row.itemId()).param("expectedVersion","0"),other)).andExpect(status().isNotFound());
        for(String q:List.of("0","-1","1.5","2147483648","\"2\""))mvc.perform(csrf(post("/api/cart/items").header("Idempotency-Key",key()).contentType(MediaType.APPLICATION_JSON).content("{\"productId\":"+row.productId()+",\"quantity\":"+q+"}"),own)).andExpect(status().isBadRequest());
        mvc.perform(csrf(put("/api/cart/items/"+row.itemId()).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":3,\"expectedVersion\":0}"),own)).andExpect(status().isOk());
        mvc.perform(csrf(delete("/api/cart/items/"+row.itemId()).param("expectedVersion","0"),own)).andExpect(status().isConflict());
    }
}
