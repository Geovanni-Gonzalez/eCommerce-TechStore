package com.techstore.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.techstore.catalog.domain.Product;
import com.techstore.catalog.dto.ProductPageResponse;
import com.techstore.catalog.dto.ProductResponse;
import com.techstore.catalog.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ProductServiceTest {

    @Test
    void emptyCatalogReturnsAnEmptyPageWithZeroTotals() {
        var service = new ProductService(new FakeProductRepository(List.of()));

        ProductPageResponse response = service.listActiveProducts(0, 12);

        assertEquals(List.of(), response.items());
        assertEquals(0, response.page());
        assertEquals(12, response.size());
        assertEquals(0, response.totalElements());
        assertEquals(0, response.totalPages());
    }

    @Test
    void paginatesOnlyActiveProductsInIdOrder() {
        var service = new ProductService(new FakeProductRepository(List.of(
                product(4, true),
                product(2, false),
                product(3, true),
                product(1, true)
        )));

        ProductPageResponse first = service.listActiveProducts(0, 2);
        ProductPageResponse second = service.listActiveProducts(1, 2);

        assertEquals(List.of(1L, 3L),
                first.items().stream().map(ProductResponse::id).toList());
        assertEquals(List.of(4L),
                second.items().stream().map(ProductResponse::id).toList());
        assertEquals(3, second.totalElements());
        assertEquals(2, second.totalPages());
        assertEquals(1, second.page());
        assertEquals(2, second.size());
    }

    @Test
    void pageAfterLastReturnsEmptyItemsAndRetainsTotals() {
        var service = new ProductService(new FakeProductRepository(
                List.of(product(1, true))));

        ProductPageResponse response = service.listActiveProducts(10, 12);

        assertTrue(response.items().isEmpty());
        assertEquals(1, response.totalElements());
        assertEquals(1, response.totalPages());
    }

    @Test
    void largePageIndexDoesNotOverflowOffset() {
        var service = new ProductService(new FakeProductRepository(
                List.of(product(1, true))));

        ProductPageResponse response = service.listActiveProducts(
                Integer.MAX_VALUE, 100);

        assertTrue(response.items().isEmpty());
        assertEquals(Integer.MAX_VALUE, response.page());
    }

    @Test
    void rejectsInvalidPageAndPageSize() {
        var service = new ProductService(new FakeProductRepository(List.of()));

        assertThrows(IllegalArgumentException.class,
                () -> service.listActiveProducts(-1, 12));
        assertThrows(IllegalArgumentException.class,
                () -> service.listActiveProducts(0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.listActiveProducts(0, 101));
    }

    @Test
    void returnsActiveProductWithPrecisePriceAndNoInventedCategory() {
        var service = new ProductService(new FakeProductRepository(List.of(
                product(1, true),
                product(2, false)
        )));

        ProductResponse response = service.findActiveProductById(1L).orElseThrow();

        assertEquals(1L, response.id());
        assertEquals(new BigDecimal("12990.50"), response.priceCrc());
        assertTrue(response.active());
        assertEquals(null, response.category());
        assertEquals(null, response.imageUrl());
        assertFalse(service.findActiveProductById(2L).isPresent());
        assertFalse(service.findActiveProductById(999L).isPresent());
    }

    @Test
    void rejectsNonPositiveProductIds() {
        var service = new ProductService(new FakeProductRepository(List.of()));

        assertThrows(IllegalArgumentException.class,
                () -> service.findActiveProductById(0));
        assertThrows(IllegalArgumentException.class,
                () -> service.findActiveProductById(-5));
    }

    @Test
    void serializedDtoOmitsUnavailableOptionalFields() {
        ProductResponse response = ProductResponse.from(product(1, true));
        String json = JsonMapper.builder().build().writeValueAsString(response);

        assertTrue(json.contains("\"priceCrc\":12990.50"));
        assertTrue(json.contains("\"active\":true"));
        assertFalse(json.contains("\"category\""));
        assertFalse(json.contains("\"imageUrl\""));
    }

    @Test
    void responsePageMakesDefensiveCopyOfItems() {
        var service = new ProductService(new FakeProductRepository(
                List.of(product(1, true))));

        ProductPageResponse response = service.listActiveProducts(0, 12);

        assertThrows(UnsupportedOperationException.class,
                () -> response.items().clear());
    }

    private static Product product(long id, boolean active) {
        return new Product(
                id,
                null,
                "TEC-" + id,
                "Producto " + id,
                "Descripción de prueba",
                new BigDecimal("12990.50"),
                10,
                active
        );
    }

    /**
     * Sustituye el acceso real a PostgreSQL: demuestra DIP sin dependencias
     * de infraestructura en las pruebas del servicio.
     */
    private record FakeProductRepository(List<Product> products)
            implements ProductRepository {

        @Override
        public List<Product> findActivePage(int limit, long offset) {
            return products.stream()
                    .filter(Product::active)
                    .sorted(Comparator.comparing(Product::id))
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<Product> findActiveById(long id) {
            return products.stream()
                    .filter(Product::active)
                    .filter(product -> product.id() == id)
                    .findFirst();
        }

        @Override
        public long countActive() {
            return products.stream().filter(Product::active).count();
        }
    }
}
