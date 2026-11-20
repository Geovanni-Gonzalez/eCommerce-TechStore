package com.techstore.cart.controller;

import com.techstore.cart.dto.CartItemRequest;
import com.techstore.cart.dto.CartResponse;
import com.techstore.cart.service.CartService;
import com.techstore.users.security.SessionPrincipal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Control HTTP sin reglas de precio o SQL. El propietario se toma únicamente
 * del token autenticado por Spring Security, nunca del JSON o de la URL.
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService service;

    public CartController(CartService service) {
        this.service = service;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public CartResponse getCart(@AuthenticationPrincipal SessionPrincipal user) {
        return service.getCart(user.id());
    }

    @PostMapping(value = "/items",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public CartResponse addItem(
            @AuthenticationPrincipal SessionPrincipal user,
            @RequestBody Map<String, Object> body) {
        var request = CartItemRequest.create(body);
        return service.addItem(user.id(), request.productId(), request.quantity());
    }

    @PatchMapping(value = "/items/{productId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public CartResponse updateQuantity(
            @AuthenticationPrincipal SessionPrincipal user,
            @PathVariable long productId,
            @RequestBody Map<String, Object> body) {
        int quantity = CartItemRequest.updateQuantity(body);
        return service.setQuantity(user.id(), productId, quantity);
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Void> removeItem(
            @AuthenticationPrincipal SessionPrincipal user,
            @PathVariable long productId) {
        service.removeItem(user.id(), productId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(
            @AuthenticationPrincipal SessionPrincipal user) {
        service.clear(user.id());
        return ResponseEntity.noContent().build();
    }
}
