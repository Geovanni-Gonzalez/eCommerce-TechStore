package com.techstore.catalog.controller;

import com.techstore.catalog.dto.ProductPageResponse;
import com.techstore.catalog.dto.ProductResponse;
import com.techstore.catalog.service.ProductService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador HTTP del catálogo público.
 *
 * GRASP Controller: traduce solicitudes REST en casos de uso, sin conocer SQL.
 * ProductService concentra la lógica de negocio y las reglas de paginación.
 */
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
            @RequestParam(name = "size", defaultValue = "12") int size) {
        return productService.listActiveProducts(page, size);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ProductResponse getProductById(@PathVariable("id") long id) {
        return productService.findActiveProductById(id)
                .orElseThrow(ProductNotFoundException::new);
    }
}
