package com.techstore.catalog.controller;

import com.techstore.catalog.dto.ProductFilterOptionsResponse;
import com.techstore.catalog.dto.ProductPageResponse;
import com.techstore.catalog.dto.ProductResponse;
import com.techstore.catalog.service.ProductService;
import java.math.BigDecimal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GRASP Controller: traduce HTTP a casos de uso, sin lógica de SQL. */
@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ProductPageResponse listProducts(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "12") int size,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "brand", required = false) String brand,
            @RequestParam(name = "minPriceCrc", required = false) BigDecimal minPriceCrc,
            @RequestParam(name = "maxPriceCrc", required = false) BigDecimal maxPriceCrc) {
        return productService.searchActiveProducts(page, size,
                q, categoryId, brand, minPriceCrc, maxPriceCrc);
    }

    @GetMapping(value = "/filters", produces = MediaType.APPLICATION_JSON_VALUE)
    public ProductFilterOptionsResponse listFilterOptions() {
        return productService.availableFilters();
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ProductResponse getProductById(@PathVariable("id") long id) {
        return productService.findActiveProductById(id)
                .orElseThrow(ProductNotFoundException::new);
    }
}
