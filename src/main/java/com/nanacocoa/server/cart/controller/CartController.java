package com.nanacocoa.server.cart.controller;
import com.nanacocoa.server.cart.service.CartService;
import com.nanacocoa.server.cart.dto.reqeust.CartRequests.*;
import com.nanacocoa.server.cart.dto.response.CartResponse;
import com.nanacocoa.server.common.response.SuccessMessage;
import com.nanacocoa.server.common.userdetails.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
@RestController @RequestMapping("/api/cart") @RequiredArgsConstructor
public class CartController {
    private final CartService service;
    @GetMapping public ResponseEntity<SuccessMessage<CartResponse>> get(@AuthenticationPrincipal UserDetailsImpl user){return result(service.get(user));}
    @PostMapping("/items") public ResponseEntity<SuccessMessage<CartResponse>> add(@Valid @RequestBody Add request,@RequestHeader(value="Idempotency-Key",required=false) String key,@AuthenticationPrincipal UserDetailsImpl user){return result(service.add(request,key,user));}
    @PutMapping("/items/{id}") public ResponseEntity<SuccessMessage<CartResponse>> update(@PathVariable Long id,@Valid @RequestBody Update request,@AuthenticationPrincipal UserDetailsImpl user){return result(service.update(id,request,user));}
    @DeleteMapping("/items/{id}") public ResponseEntity<SuccessMessage<CartResponse>> delete(@PathVariable Long id,@RequestParam Long expectedVersion,@AuthenticationPrincipal UserDetailsImpl user){return result(service.delete(id,expectedVersion,user));}
    private ResponseEntity<SuccessMessage<CartResponse>> result(CartResponse data){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new SuccessMessage<>("장바구니조회성공",data));}
}
