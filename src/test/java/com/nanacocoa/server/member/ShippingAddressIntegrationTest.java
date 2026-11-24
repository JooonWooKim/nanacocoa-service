package com.nanacocoa.server.member;

import com.nanacocoa.server.member.entity.*;
import com.nanacocoa.server.member.repository.*;
import com.nanacocoa.server.member.service.ShippingAddressService;
import com.nanacocoa.server.member.dto.reqeust.ShippingAddressRequests.*;
import com.nanacocoa.server.member.dto.response.ShippingAddressResponse;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.common.exception.NanacocoaException;
import com.nanacocoa.server.order.dto.reqeust.*;
import com.nanacocoa.server.order.service.OrderService;
import com.nanacocoa.server.cart.dto.reqeust.CartRequests;
import com.nanacocoa.server.cart.service.CartService;
import com.nanacocoa.server.products.entity.*;
import com.nanacocoa.server.products.repository.ProductsRepository;
import com.nanacocoa.server.payment.pg.TossPaymentsClient;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.nanacocoa.server.CsrfRequests.csrf;

@ActiveProfiles("test") @AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.open-in-view=false", "spring.sql.init.mode=never", "cart.cleanup.initial-delay=1h"})
class ShippingAddressIntegrationTest {
    static final String API = "/api/members/me/shipping-addresses";
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        String url = System.getenv("ADDRESS_TEST_DB_URL");
        r.add("spring.datasource.url", () -> url == null ? "jdbc:h2:mem:addresses;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000" : url);
        r.add("spring.datasource.driver-class-name", () -> url == null ? "org.h2.Driver" : "com.mysql.cj.jdbc.Driver");
        r.add("spring.datasource.username", () -> url == null ? "sa" : "root");
        r.add("spring.datasource.password", () -> "");
    }
    @Autowired ShippingAddressService service;
    @Autowired MemberRepository members;
    @Autowired ShippingAddressBookRepository books;
    @Autowired ShippingAddressRepository addresses;
    @MockitoSpyBean ShippingAddressRegistrationRepository registrations;
    @Autowired OrderService orders;
    @Autowired CartService cart;
    @Autowired ProductsRepository products;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean TossPaymentsClient pg;
    UserDetailsImpl buyer;
    @BeforeEach void setup() { buyer = identity(); }
    UserDetailsImpl identity() {
        var member = Member.builder().email(UUID.randomUUID()+"@example.test").name("가상 회원")
            .password("encoded").address("기존 가상 프로필 주소").phoneNumber("010-0000-0000").build();
        member = members.saveAndFlush(member); return new UserDetailsImpl(member, member.getEmail());
    }
    Details details(String label) { return new Details(label,"가상 수령인","010-0000-0000","12345","가상 기본 주소","가상 상세"); }
    String key() { return UUID.randomUUID().toString(); }
    ShippingAddressResponse create(String label) {
        return service.create(new Create(details(label), false, service.list(buyer).revision()), key(), null, buyer);
    }
    ShippingAddressResponse.Item first() { return service.list(buyer).items().get(0); }
    MockHttpSession session(UserDetailsImpl user) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities()));
        var session = new MockHttpSession();session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);return session;
    }
    @Test void emptyBookDoesNotMigrateProfileAndCrudPersistsAcrossLogin() {
        assertThat(service.list(buyer).items()).isEmpty();
        var created = create("집"); var row = created.items().get(0);assertThat(row.defaultAddress()).isTrue();
        var updated = service.update(row.id(),new Update(details("수정"),row.version(),created.revision()),null,buyer);
        var relogin = new UserDetailsImpl(members.findByEmail(buyer.getUsername()).orElseThrow(),buyer.getUsername());
        assertThat(service.list(relogin).items().get(0).label()).isEqualTo("수정");
        assertThat(members.findByEmail(buyer.getUsername()).orElseThrow().getAddress()).isEqualTo("기존 가상 프로필 주소");
        row = updated.items().get(0);assertThat(service.delete(row.id(),row.version(),updated.revision(),null,buyer).items()).isEmpty();
        verifyNoInteractions(pg);
    }
    @Test void deletedDefaultDoesNotPromoteRemainingOrNewAddress() {
        create("첫 주소");var book = create("둘째 주소");var row = book.items().get(0);
        book = service.delete(row.id(),row.version(),book.revision(),null,buyer);
        assertThat(book.items()).noneMatch(ShippingAddressResponse.Item::defaultAddress);
        book = create("추가 주소");assertThat(book.items()).noneMatch(ShippingAddressResponse.Item::defaultAddress);
        for (var item : book.items()) service.delete(item.id(),item.version(),service.list(buyer).revision(),null,buyer);
        assertThat(create("다시 최초").items().get(0).defaultAddress()).isTrue();
    }
    @Test void registrationReplaySurvivesModificationAndDeletionWithoutRecreating() {
        var request = new Create(details("집"),false,0L);String key=key();
        var book=service.create(request,key,null,buyer);var row=book.items().get(0);
        service.delete(row.id(),row.version(),book.revision(),null,buyer);
        assertThat(service.create(request,key,null,buyer).items()).isEmpty();
        assertThatThrownBy(()->service.create(new Create(details("다른 입력"),false,0L),key,null,buyer)).isInstanceOf(NanacocoaException.class);
    }
    @Test void staleItemOrBookRevisionCannotOverwrite() {
        var a=create("첫 주소").items().get(0);var book=create("둘째 주소");
        assertThatThrownBy(()->service.update(a.id(),new Update(details("변경"),a.version(),1L),null,buyer)).isInstanceOf(NanacocoaException.class);
        var changed=service.update(a.id(),new Update(details("변경"),a.version(),book.revision()),null,buyer);
        assertThatThrownBy(()->service.delete(a.id(),a.version(),changed.revision(),null,buyer)).isInstanceOf(NanacocoaException.class);
    }
    @Test void concurrentSameKeyCreatesOneDefaultAddress() throws Exception {
        String key=key();var request=new Create(details("집"),false,0L);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var futures=pool.invokeAll(List.of(()->service.create(request,key,null,buyer),()->service.create(request,key,null,buyer)));
            for(var f:futures)f.get();
            assertThat(service.list(buyer).items()).hasSize(1);assertThat(first().defaultAddress()).isTrue();
        } finally {pool.shutdownNow();}
    }
    @Test void concurrentDifferentDefaultTargetsCannotBothUseSameRevision() throws Exception {
        create("첫 주소");var book=create("둘째 주소");var pool=Executors.newFixedThreadPool(2);
        try {
            var tasks=new ArrayList<Callable<Boolean>>();
            for(var row:book.items())tasks.add(()->{try{service.designate(row.id(),new Version(row.version(),book.revision()),null,buyer);return true;}catch(NanacocoaException conflict){return false;}});
            var outcomes=new ArrayList<Boolean>();for(var f:pool.invokeAll(tasks))outcomes.add(f.get());
            assertThat(outcomes).containsExactlyInAnyOrder(true,false);
            assertThat(service.list(buyer).items().stream().filter(ShippingAddressResponse.Item::defaultAddress)).hasSize(1);
        } finally {pool.shutdownNow();}
    }
    @Test void databaseRejectsDefaultPointerToAnotherOwnersAddress() {
        create("집");var other=identity();var otherBook=service.create(new Create(details("다른 회원 집"),false,0L),key(),null,other);
        Long member=members.findByEmail(buyer.getUsername()).orElseThrow().getId();
        assertThatThrownBy(()->jdbc.update("update shipping_address_books set default_address_id=? where member_id=?",otherBook.items().get(0).id(),member)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(first().defaultAddress()).isTrue();
    }
    @Test void registrationFailureRollsBackAddressDefaultAndRevision() {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic registration failure")).when(registrations).save(any());
        assertThatThrownBy(()->create("집")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(service.list(buyer).items()).isEmpty();assertThat(service.list(buyer).revision()).isZero();
    }
    @Test void composedAddressLengthAndInvalidPostalAreRejectedWithoutWrites() {
        for(var d:List.of(new Details("집","가상","010-0000-0000","1234","가상",""),
                         new Details("집","가상","010-0000-0000","12345","가".repeat(495),""))) {
            assertThatThrownBy(()->service.create(new Create(d,false,0L),key(),null,buyer)).isInstanceOf(NanacocoaException.class);
        }
        assertThat(service.list(buyer).items()).isEmpty();
    }
    @Test void directAndMultiProductOrdersKeepSnapshotsAfterAddressEditAndDelete() {
        create("집");var original=first();String shipping="["+original.postalCode()+"] "+original.basicAddress()+" "+original.detailAddress();
        var p=Products.builder().name("address-"+key()).price(41000L).summary("가상").detailTitle("가상").description("가상").build();p.updateInventory(20,SaleStatus.ON_SALE,0);p=products.saveAndFlush(p);
        var q=Products.builder().name("address-"+key()).price(42000L).summary("가상").detailTitle("가상").description("가상").build();q.updateInventory(20,SaleStatus.ON_SALE,0);q=products.saveAndFlush(q);
        var cartBook=cart.add(new CartRequests.Add(p.getId(),1),key(),buyer);cartBook=cart.add(new CartRequests.Add(q.getId(),1),key(),buyer);
        var selected=cartBook.items();
        var multi=new CreateOrderRequest(original.recipientName(),original.phoneNumber(),shipping,null,
            selected.stream().map(i->new OrderItemRequest(i.productId(),i.quantity())).toList(),
            selected.stream().map(i->new CartRequests.Selection(i.itemId(),i.version(),i.quantity(),i.unitPrice())).toList());
        String submission=key();var saved=orders.createOrder(multi,submission,null,buyer).order();
        var direct=orders.createOrder(new CreateOrderRequest(original.recipientName(),original.phoneNumber(),shipping,null,List.of(new OrderItemRequest(p.getId(),1))),key(),null,buyer).order();
        var updated=service.update(original.id(),new Update(details("수정"),original.version(),service.list(buyer).revision()),null,buyer);
        var row=updated.items().get(0);service.delete(row.id(),row.version(),updated.revision(),null,buyer);
        assertThat(orders.createOrder(multi,submission,null,buyer).order().getOrderId()).isEqualTo(saved.getOrderId());
        assertThat(orders.getOrder(saved.getOrderId(),buyer).shippingAddress()).isEqualTo(shipping);
        assertThat(orders.getOrder(direct.getOrderId(),buyer).shippingAddress()).isEqualTo(shipping);
        verifyNoInteractions(pg);
    }
    @Test void v12PreservesV11MemberOrderAndCartDataWithoutAddressMigration() throws Exception {
        String mysql = System.getenv("ADDRESS_MIGRATION_TEST_DB_URL");
        String url = mysql == null ? "jdbc:h2:mem:address-upgrade;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1" : mysql;
        String user = mysql == null ? "sa" : "root";
        org.flywaydb.core.Flyway.configure().dataSource(url,user,"").locations("classpath:db/migration").target("11").load().migrate();
        try(var connection=java.sql.DriverManager.getConnection(url,user,"");var sql=connection.createStatement()) {
            sql.executeUpdate("insert into members (id,email,name,password,is_admin,address,phone_number,email_receive_consent,created_at) values (1,'legacy@example.test','가상 회원','encoded',false,'가상 프로필 주소','010-0000-0000','N',current_timestamp)");
            sql.executeUpdate("insert into products (id,name,price,summary,detail_title,description,created_at) values (1,'legacy',41000,'가상','가상','가상',current_timestamp)");
            sql.executeUpdate("insert into orders (id,order_id,member_id,total_amount,status,version,recipient_name,phone_number,shipping_address,created_at,updated_at) values (1,'legacy-address-order',1,41000,'CREATED',0,'가상 수령인','010-0000-0000','가상 주문 주소',current_timestamp,current_timestamp)");
            sql.executeUpdate("insert into cart_items (id,member_id,product_id,quantity,version) values (1,1,1,2,0)");
            sql.executeUpdate("insert into cart_additions (member_id,request_key,product_id,quantity) values (1,'11111111-1111-4111-8111-111111111111',1,2)");
            sql.executeUpdate("insert into cart_checkouts (id,order_id,member_id,state) values (1,1,1,'WAITING')");
            sql.executeUpdate("insert into cart_checkout_items (checkout_id,item_id,item_version,product_id,quantity,unit_price,outcome) values (1,1,0,1,1,41000,'WAITING')");
        }
        assertThat(org.flywaydb.core.Flyway.configure().dataSource(url,user,"").locations("classpath:db/migration").load().migrate().migrationsExecuted).isEqualTo(7);
        try(var connection=java.sql.DriverManager.getConnection(url,user,"");var sql=connection.createStatement()) {
            for(String table:List.of("shipping_addresses","shipping_address_books","shipping_address_registrations")) {
                try(var rows=sql.executeQuery("select count(*) from "+table)){assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isZero();}
            }
            try(var rows=sql.executeQuery("select address from members where id=1")){assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("가상 프로필 주소");}
            try(var rows=sql.executeQuery("select shipping_address from orders where id=1")){assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("가상 주문 주소");}
            try(var rows=sql.executeQuery("select quantity,version from cart_items where id=1")){assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isEqualTo(2);assertThat(rows.getLong(2)).isZero();}
            try(var rows=sql.executeQuery("select outcome from cart_checkout_items where checkout_id=1")){assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("WAITING");}
        }
    }

    @Test void httpOwnerCsrfAccountGuardAndProfileVerificationRemainIndependent() throws Exception {
        create("집");var row=first();var own=session(buyer);var other=session(identity());
        mvc.perform(get(API)).andExpect(status().isUnauthorized());
        mvc.perform(get(API).session(own)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/api/members/me").session(own)).andExpect(status().isForbidden());
        String data=json.writeValueAsString(new Update(details("변경"),row.version(),service.list(buyer).revision()));
        mvc.perform(put(API+"/"+row.id()).session(own).contentType(MediaType.APPLICATION_JSON).content(data)).andExpect(status().isForbidden());
        mvc.perform(put(API+"/"+row.id()).session(other).with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(data)).andExpect(status().isNotFound());
        mvc.perform(put(API+"/"+row.id()).session(own).with(csrf(mvc)).header("X-Checkout-Account","different").contentType(MediaType.APPLICATION_JSON).content(data)).andExpect(status().isConflict());
        mvc.perform(post(API).session(own).with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new Create(details("집"),false,1L)))).andExpect(status().isBadRequest());
    }
    @Test void httpValidationAndRegistrationReplayAreSafe() throws Exception {
        var own=session(buyer);String key=key();String payload=json.writeValueAsString(new Create(details("집"),false,0L));
        for(int i=0;i<2;i++)mvc.perform(post(API).session(own).with(csrf(mvc)).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));
        mvc.perform(post(API).session(own).with(csrf(mvc)).header("Idempotency-Key",key()).contentType(MediaType.APPLICATION_JSON).content(payload.replace("12345","wrong"))).andExpect(status().isBadRequest());
        mvc.perform(delete(API+"/"+first().id()).param("expectedVersion","0").param("expectedRevision","1").session(own).with(csrf(mvc))).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
    }
}
