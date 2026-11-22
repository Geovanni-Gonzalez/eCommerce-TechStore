package com.techstore.reviews;

import com.techstore.users.security.SessionPrincipal;
import java.net.URI;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints CLIENT del backend BKL-050. El formulario público será BKL-051.
 * La identidad del cliente se obtiene del principal, nunca del body.
 */
@RestController
@RequestMapping("/api/reviews/products")
public class ProductReviewController {
    private final ProductReviewService service;

    public ProductReviewController(ProductReviewService service) {
        this.service = service;
    }

    @GetMapping(value="/{productId}/eligibility",
            produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReviewEligibility> eligibility(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable long productId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.eligibility(principal.id(),productId));
    }

    @GetMapping(value="/{productId}/mine",
            produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductReview> mine(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable long productId) {
        return service.mine(principal.id(),productId)
                .map(r->ResponseEntity.ok().cacheControl(CacheControl.noStore())
                        .body(r))
                .orElseGet(()->ResponseEntity.notFound()
                        .cacheControl(CacheControl.noStore()).build());
    }

    @PostMapping(value="/{productId}",
            consumes=MediaType.APPLICATION_JSON_VALUE,
            produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductReview> create(
            @AuthenticationPrincipal SessionPrincipal principal,
            @PathVariable long productId,
            @RequestBody Map<String,Object> body) {
        ProductReview result=service.create(principal.id(),productId,ReviewInput.parse(body));
        return ResponseEntity.created(URI.create(
                "/api/reviews/products/"+productId+"/mine"))
                .cacheControl(CacheControl.noStore()).body(result);
    }
}
