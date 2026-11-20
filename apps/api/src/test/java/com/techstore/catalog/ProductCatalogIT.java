package com.techstore.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techstore.catalog.repository.ProductRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Integración real: PostgreSQL + Flyway V1 + JDBC + servicio + HTTP MockMvc.
 *
 * Failsafe la ejecuta en mvn verify (no en mvn test).
 * Exige base exclusiva techstore_test: se niega a modificar otra base.
 * Las inserciones y la limpieza se revierten al finalizar cada test.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class ProductCatalogIT {

    private static final MediaType PROBLEM_JSON =
            MediaType.parseMediaType("application/problem+json");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProductRepository repository;

    @Autowired
    private WebApplicationContext webContext;

    private MockMvc mvc;

    @BeforeEach
    void isolateTestData() {
        String database = jdbc.queryForObject(
                "SELECT current_database()", String.class);
        assertEquals("techstore_test", database,
                "Las pruebas de integración solo pueden alterar techstore_test");
        // Ejecutado dentro de la transacción de prueba; se revierte con rollback.
        jdbc.update("DELETE FROM products");
        mvc = MockMvcBuilders.webAppContextSetup(webContext).build();
    }

    @Test
    void emptyCatalogReturnsValidPageFromRealDatabase() throws Exception {
        assertEquals(0L, repository.countActive());

        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(12))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void jdbcFiltersInactiveAndMapsMoneyAndNullableCategory() throws Exception {
        long activeId = insertProduct("IT-ACTIVE", true, new BigDecimal("12990.50"));
        long hiddenId = insertProduct("IT-HIDDEN", false, new BigDecimal("15000.00"));

        assertEquals(1L, repository.countActive());
        var active = repository.findActiveById(activeId).orElseThrow();
        assertEquals(new BigDecimal("12990.50"), active.priceCrc());
        assertEquals(null, active.categoryId());
        assertTrue(active.active());
        assertFalse(repository.findActiveById(hiddenId).isPresent());
        assertFalse(repository.findActiveById(999999L).isPresent());

        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(activeId))
                .andExpect(jsonPath("$.items[0].priceCrc").value(12990.50));

        mvc.perform(get("/api/products/{id}", activeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceCrc").value(12990.50))
                .andExpect(jsonPath("$.category").doesNotExist());

        mvc.perform(get("/api/products/{id}", hiddenId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void realSqlPaginationReturnsStableSecondPage() throws Exception {
        long first = insertProduct("IT-ONE", true, new BigDecimal("100.00"));
        long second = insertProduct("IT-TWO", true, new BigDecimal("200.00"));
        long third = insertProduct("IT-THREE", true, new BigDecimal("300.00"));
        insertProduct("IT-FOUR", false, new BigDecimal("400.00"));

        assertEquals(3L, repository.countActive());
        assertEquals(
                java.util.List.of(first, second),
                repository.findActivePage(2, 0L).stream().map(p -> p.id()).toList());
        assertEquals(third, repository.findActivePage(2, 2L).getFirst().id());

        mvc.perform(get("/api/products").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(third))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void invalidInputsProduceProblemDetailsWithRealSpringContext() throws Exception {
        mvc.perform(get("/api/products").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(get("/api/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(get("/api/products/no-es-numero"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private long insertProduct(String sku, boolean active, BigDecimal price) {
        Long id = jdbc.queryForObject("""
                INSERT INTO products (sku, name, description, price_crc, stock, active)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, sku, "Accesorio " + sku, "Prueba JDBC",
                price, 7, active);
        if (id == null) {
            throw new IllegalStateException("PostgreSQL no devolvió ID");
        }
        return id;
    }
}
