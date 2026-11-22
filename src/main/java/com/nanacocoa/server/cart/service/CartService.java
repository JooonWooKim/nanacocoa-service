package com.nanacocoa.server.cart.service;
import com.nanacocoa.server.cart.entity.*;
import com.nanacocoa.server.cart.repository.*;
import com.nanacocoa.server.cart.dto.reqeust.CartRequests.*;
import com.nanacocoa.server.cart.dto.response.CartResponse;
import com.nanacocoa.server.common.exception.*;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import com.nanacocoa.server.member.entity.Member;
import com.nanacocoa.server.member.repository.MemberRepository;
import com.nanacocoa.server.products.repository.ProductsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import lombok.RequiredArgsConstructor;
import java.util.*;
import static com.nanacocoa.server.common.exception.ErrorCode.*;
@Service @RequiredArgsConstructor
public class CartService {
    private final CartItemRepository items;
    private final CartAdditionRepository additions;
    private final MemberRepository members;
    private final ProductsRepository products;
    @Transactional(readOnly=true)
    public CartResponse get(UserDetailsImpl user){return view(member(user,false).getId());}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public CartResponse add(Add request,String rawKey,UserDetailsImpl user){
        Member member=member(user,true); String key=key(rawKey);
        var replay=additions.findByMemberIdAndRequestKey(member.getId(),key);
        if(replay.isPresent()){
            var saved=replay.get();
            if(!saved.getProductId().equals(request.productId()) || saved.getQuantity()!=request.quantity())throw new NanacocoaException(CART_CONFLICT);
            return view(member.getId());
        }
        var product=products.findById(request.productId()).orElseThrow(()->new NanacocoaException(NOT_FOUND_PRODUCTS));
        if(!"ON_SALE".equals(product.getAvailability()))throw new NanacocoaException(INSUFFICIENT_STOCK);
        var item=items.productForUpdate(member.getId(),product.getId()).orElse(null);
        int quantity;
        try{quantity=Math.addExact(item==null?0:item.getQuantity(),request.quantity());Math.multiplyExact(product.getPrice(),quantity);}
        catch(ArithmeticException e){throw new NanacocoaException(ORDER_AMOUNT_OVERFLOW);}
        if(item==null)items.save(new CartItem(member.getId(),product.getId(),quantity));else item.quantity(quantity);
        additions.save(new CartAddition(member.getId(),key,product.getId(),request.quantity()));items.flush();
        return view(member.getId());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public CartResponse update(Long id,Update request,UserDetailsImpl user){
        var member=member(user,true);var item=owned(member.getId(),id);item.expect(request.expectedVersion());
        var product=products.findById(item.getProductId()).orElseThrow(()->new NanacocoaException(NOT_FOUND_PRODUCTS));
        try{Math.multiplyExact(product.getPrice(),request.quantity());}catch(ArithmeticException e){throw new NanacocoaException(ORDER_AMOUNT_OVERFLOW);}
        item.quantity(request.quantity());items.flush();return view(member.getId());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public CartResponse delete(Long id,Long version,UserDetailsImpl user){
        var member=member(user,true);var item=owned(member.getId(),id);item.expect(version);items.delete(item);items.flush();return view(member.getId());
    }
    private CartItem owned(Long member,Long id){return items.ownedForUpdate(member,id).orElseThrow(()->new NanacocoaException(CART_NOT_FOUND));}
    private Member member(UserDetailsImpl user,boolean lock){
        if(user==null)throw new NanacocoaException(AUTHENTICATION_REQUIRED);
        return (lock?members.findByEmailForUpdate(user.getUsername()):members.findByEmail(user.getUsername())).orElseThrow(()->new NanacocoaException(AUTHENTICATION_REQUIRED));
    }
    private String key(String raw){
        if(raw==null || !raw.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))throw new NanacocoaException(INVALID_REQUEST);
        return UUID.fromString(raw).toString();
    }
    private CartResponse view(Long member){
        var rows=items.findByMemberIdOrderById(member);var ids=rows.stream().map(CartItem::getProductId).toList();
        var byId=new HashMap<Long,com.nanacocoa.server.products.entity.Products>();products.findAllById(ids).forEach(p->byId.put(p.getId(),p));
        var values=rows.stream().map(i->{var p=byId.get(i.getProductId());return new CartResponse.Item(i.getId(),i.getVersion(),i.getProductId(),p==null?"판매 종료 상품":p.getName(),p==null?null:p.getImageUrl(),p==null?0:p.getPrice(),i.getQuantity(),p==null?"STOPPED":p.getAvailability());}).toList();
        return new CartResponse(values,rows.stream().mapToLong(CartItem::getQuantity).sum());
    }
}
