package com.techstore.catalog.admin;

import com.techstore.catalog.dto.CategoryResponse;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Solamente /api/admin/**: ApiSecurityConfig exige ROLE_ADMIN. */
@RestController
@RequestMapping("/api/admin/catalog")
public final class CatalogAdminController {
    private final CatalogAdminService service;
    public CatalogAdminController(CatalogAdminService service) {
        this.service = service;
    }

    @GetMapping(value="/products",produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdminProductPage> products(
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.products(page,size));
    }

    @PostMapping(value="/products",consumes=MediaType.APPLICATION_JSON_VALUE,
            produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdminProduct> addProduct(@RequestBody Map<String,Object> body) {
        var value = service.addProduct(body);
        return ResponseEntity.created(URI.create("/api/admin/catalog/products/"+value.id()))
                .cacheControl(CacheControl.noStore()).body(value);
    }

    @PutMapping(value="/products/{id}",consumes=MediaType.APPLICATION_JSON_VALUE,
            produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdminProduct> editProduct(@PathVariable long id,
            @RequestBody Map<String,Object> body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.editProduct(id,body));
    }

    @PatchMapping(value="/products/{id}/status",consumes=MediaType.APPLICATION_JSON_VALUE,
            produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdminProduct> status(@PathVariable long id,
            @RequestBody Map<String,Object> body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.changeStatus(id,body));
    }

    @GetMapping(value="/categories",produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<CategoryResponse>> categories() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.categories());
    }

    @PostMapping(value="/categories",consumes=MediaType.APPLICATION_JSON_VALUE,
            produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CategoryResponse> addCategory(@RequestBody Map<String,Object> body) {
        var value=service.addCategory(body);
        return ResponseEntity.created(URI.create("/api/admin/catalog/categories/"+value.id()))
                .cacheControl(CacheControl.noStore()).body(value);
    }

    @PutMapping(value="/categories/{id}",consumes=MediaType.APPLICATION_JSON_VALUE,
            produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CategoryResponse> renameCategory(@PathVariable long id,
            @RequestBody Map<String,Object> body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.renameCategory(id,body));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable long id) {
        service.deleteCategory(id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
