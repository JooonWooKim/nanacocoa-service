package com.nanacocoa.server.cart.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity
@Table(name="cart_additions",uniqueConstraints=@UniqueConstraint(name="uk_cart_addition_key",columnNames={"member_id","request_key"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class CartAddition {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="member_id",nullable=false) private Long memberId;
    @Column(name="request_key",nullable=false,length=36) private String requestKey;
    @Column(name="product_id",nullable=false) private Long productId;
    @Column(nullable=false) private int quantity;
    public CartAddition(Long memberId,String key,Long productId,int quantity) {
        this.memberId=memberId;requestKey=key;this.productId=productId;this.quantity=quantity;
    }
}
