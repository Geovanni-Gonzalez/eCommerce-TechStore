package com.techstore.payments.sessions;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Con configuración predeterminada NO existe un checkout sandbox activo. */
@SpringBootTest
@ActiveProfiles("integration")
class SandboxCheckoutSessionDisabledIT {
    @Autowired WebApplicationContext context;

    @Test
    void disabledByDefaultDoesNotExposeTokenEndpoints() throws Exception {
        assertEquals(0,
                context.getBeanNamesForType(SandboxCheckoutSessionController.class).length);
        var mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
        mvc.perform(post("/api/payments/sandbox/sessions/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"abc\"}"))
                .andExpect(status().isNotFound());
    }
}
