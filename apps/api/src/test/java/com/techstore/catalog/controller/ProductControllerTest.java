package com.techstore.catalog.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techstore.catalog.domain.Product;
import com.techstore.catalog.repository.ProductRepository;
import com.techstore.catalog.service.ProductService;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Pruebas de contrato HTTP sin PostgreSQL: un repositorio en memoria
 * permite comprobar rutas, JSON, paginación y Problem Details.
 */
class ProductControllerTest {

    private static final MediaType PROBLEM_JSON =
            MediaType.parseMediaType("application/problem+json");

    @Test
    void listsActiveProductsUsingDefaultPaging() throws Exception {
        var mvc = controllerWithProducts(List.of(
                product(3, true), product(2, false), product(1, true)));

        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(12))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[1].id").value(3))
                .andExpect(jsonPath("$.items[0].priceCrc").value(12990.50))
                .andExpect(jsonPath("$.items[0].active").value(true));
    }

    @Test
    void returnsPagedProductsUsingQueryParameters() throws Exception {
        var mvc = controllerWithProducts(List.of(
                product(1, true), product(2, true), product(3, true)));

        mvc.perform(get("/api/products").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(3));
    }

    @Test
    void emptyCatalogReturns200WithEmptyItems() throws Exception {
        var mvc = controllerWithProducts(List.of());

        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void pagePastLastReturns200WithEmptyItems() throws Exception {
        var mvc = controllerWithProducts(List.of(product(1, true)));

        mvc.perform(get("/api/products").param("page", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void activeProductDetailReturns200() throws Exception {
        var mvc = controllerWithProducts(List.of(product(1, true)));

        mvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sku").value("TEC-1"))
                .andExpect(jsonPath("$.priceCrc").value(12990.50))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.category").doesNotExist())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
    }

    @Test
    void nonexistentProductReturns404ProblemDetails() throws Exception {
        var mvc = controllerWithProducts(List.of(product(1, true)));

        mvc.perform(get("/api/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Producto no encontrado"))
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void inactiveProductReturns404WithoutDisclosingItsStatus() throws Exception {
        var mvc = controllerWithProducts(List.of(product(2, false)));

        mvc.perform(get("/api/products/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void negativePageReturns400ProblemDetails() throws Exception {
        var mvc = controllerWithProducts(List.of());

        mvc.perform(get("/api/products").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void pageSizeOutOfRangeReturns400() throws Exception {
        var mvc = controllerWithProducts(List.of());

        mvc.perform(get("/api/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(get("/api/products").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void malformedParametersReturn400ProblemDetails() throws Exception {
        var mvc = controllerWithProducts(List.of());

        mvc.perform(get("/api/products").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(get("/api/products/no-es-numero"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void nonPositiveIdReturns400InsteadOf404() throws Exception {
        var mvc = controllerWithProducts(List.of());

        mvc.perform(get("/api/products/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private static MockMvc controllerWithProducts(List<Product> products) {
        var repository = new FakeProductRepository(products);
        var service = new ProductService(repository);
        var controller = new ProductController(service);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ProductExceptionHandler())
                .build();
    }

    private static Product product(long id, boolean active) {
        return new Product(
                id, null, "TEC-" + id, "Producto " + id,
                "Accesorio tecnológico", new BigDecimal("12990.50"),
                8, active);
    }

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
