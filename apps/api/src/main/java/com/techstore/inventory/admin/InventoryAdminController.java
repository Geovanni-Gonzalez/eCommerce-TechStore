package com.techstore.inventory.admin;

import com.techstore.inventory.domain.InventoryBalance;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * BKL-026: protegida por ROLE_ADMIN en ApiSecurityConfig (/api/admin/**).
 * El usuario se deriva del principal; no se aceptan actorId ni stock final
 * en el body, ni se revela el inventario a CLIENT o visitantes.
 */
@RestController
@RequestMapping("/api/admin/inventory")
public class InventoryAdminController {
    private final InventoryAdminService service;

    public InventoryAdminController(InventoryAdminService service) {
        this.service=service;
    }

    @GetMapping(produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InventoryBalancePage> list(
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.listBalances(page,size));
    }

    @GetMapping(value="/products/{productId}",
            produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InventoryBalance> balance(@PathVariable long productId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.balance(productId));
    }

    @GetMapping(value="/products/{productId}/movements",
            produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InventoryMovementPage> movements(
            @PathVariable long productId,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.movements(productId,page,size));
    }

    @PostMapping(value="/products/{productId}/adjustments",
            consumes=MediaType.APPLICATION_JSON_VALUE,
            produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InventoryAdjustmentResult> adjust(
            @AuthenticationPrincipal SessionPrincipal admin,
            @PathVariable long productId,
            @RequestBody Map<String,Object> body) {
        var result=service.adjust(admin.id(),productId,body);
        var response=result.replayed()
                ? ResponseEntity.ok()
                : ResponseEntity.created(URI.create(
                    "/api/admin/inventory/products/"+productId+"/movements"));
        return response.cacheControl(CacheControl.noStore()).body(result);
    }
}
