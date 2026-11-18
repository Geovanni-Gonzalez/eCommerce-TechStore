package com.techstore.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

class WebCorsConfigTest {

    @Test
    void registersCorsMappingForApiRoutes() {
        var config = new WebCorsConfig(
                "http://localhost:3000, http://127.0.0.1:3000");

        var registry = new InspectableCorsRegistry();
        config.addCorsMappings(registry);

        var mappings = registry.configurations();
        assertEquals(1, mappings.size());

        var cors = mappings.get("/api/**");
        assertEquals(
                List.of("http://localhost:3000", "http://127.0.0.1:3000"),
                cors.getAllowedOrigins());
        assertEquals(Boolean.FALSE, cors.getAllowCredentials());
    }

    /**
     * El método de Spring es protected; la subclase permite inspeccionar
     * únicamente la configuración generada durante esta prueba.
     */
    private static final class InspectableCorsRegistry extends CorsRegistry {

        Map<String, CorsConfiguration> configurations() {
            return super.getCorsConfigurations();
        }
    }
}
