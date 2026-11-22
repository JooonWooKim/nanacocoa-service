package com.nanacocoa.server.cart.entity;
import jakarta.persistence.*;
import lombok.*;
import java.util.*;
@Entity @Table(name="cart_checkouts")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class CartCheckout {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="order_id",nullable=false,unique=true) private Long orderId;
    @Column(name="member_id",nullable=false) private Long memberId;
    @Column(nullable=false,length=16) private String state="WAITING";
    @ElementCollection
    @CollectionTable(name="cart_checkout_items",joinColumns=@JoinColumn(name="checkout_id"))
    @OrderBy("itemId ASC") private List<Snapshot> items=new ArrayList<>();
    public CartCheckout(Long orderId,Long memberId,List<Snapshot> items){this.orderId=orderId;this.memberId=memberId;this.items.addAll(items);}
    public void ready(){if(state.equals("WAITING"))state="READY";}
    public void done(){state="DONE";}
    @Embeddable @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
    public static class Snapshot {
        @Column(name="item_id",nullable=false) private Long itemId;
        @Column(name="item_version",nullable=false) private Long itemVersion;
        @Column(name="product_id",nullable=false) private Long productId;
        @Column(nullable=false) private int quantity;
        @Column(name="unit_price",nullable=false) private Long unitPrice;
        @Column(nullable=false,length=24) private String outcome="WAITING";
        public Snapshot(Long id,Long version,Long product,int qty,Long price){itemId=id;itemVersion=version;productId=product;quantity=qty;unitPrice=price;}
        public void outcome(String result){outcome=result;}
    }
}
