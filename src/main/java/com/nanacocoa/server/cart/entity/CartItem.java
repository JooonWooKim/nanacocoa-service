package com.nanacocoa.server.cart.entity;

import jakarta.persistence.*;
import lombok.*;
import com.nanacocoa.server.common.exception.*;
import static com.nanacocoa.server.common.exception.ErrorCode.*;

@Entity
@Table(name="cart_items", uniqueConstraints=@UniqueConstraint(name="uk_cart_member_product",columnNames={"member_id","product_id"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class CartItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="member_id",nullable=false) private Long memberId;
    @Column(name="product_id",nullable=false) private Long productId;
    @Column(nullable=false) private int quantity;
    @Version @Column(nullable=false) private Long version;
    public CartItem(Long memberId, Long productId, int quantity) {
        this.memberId=memberId; this.productId=productId; this.quantity=quantity;
    }
    public void quantity(int value) { if(value<=0) throw new NanacocoaException(INVALID_REQUEST); quantity=value; }
    public void expect(Long value) { if(!java.util.Objects.equals(version,value)) throw new NanacocoaException(CART_CONFLICT); }
}
