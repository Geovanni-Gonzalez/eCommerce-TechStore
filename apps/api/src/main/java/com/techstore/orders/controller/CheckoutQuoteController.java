package com.techstore.orders.controller;

import com.techstore.orders.dto.CheckoutQuoteResponse;
import com.techstore.orders.service.CheckoutQuoteService;
import com.techstore.users.security.SessionPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controlador HTTP: las reglas de precio/impuesto/envío viven en el servicio. */
@RestController
@RequestMapping("/api/checkout")
public class CheckoutQuoteController {
    private final CheckoutQuoteService service;

    public CheckoutQuoteController(CheckoutQuoteService service) {
        this.service = service;
    }

    @GetMapping(value = "/quote", produces = MediaType.APPLICATION_JSON_VALUE)
    public CheckoutQuoteResponse getQuote(
            @AuthenticationPrincipal SessionPrincipal principal) {
        return service.quote(principal.id());
    }
}
