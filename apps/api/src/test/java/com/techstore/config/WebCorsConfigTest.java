package com.techstore.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

class WebCorsConfigTest {

    @Test
    void registersCorsMappingForApiRoutes() {
        var config = new WebCorsConfig(
                "http://localhost:3000, http://127.0.0.1:3000");

        var registry = new CorsRegistry();
        config.addCorsMappings(registry);

        var mappings = registry.getCorsConfigurations();
        assertEquals(1, mappings.size());
        var cors = mappings.get("/api/**");
        assertArrayEquals(
                new String[] {"http://localhost:3000", "http://127.0.0.1:3000"},
                cors.getAllowedOrigins().toArray(String[]::new));
        assertEquals(Boolean.FALSE, cors.getAllowCredentials());
    }
}
