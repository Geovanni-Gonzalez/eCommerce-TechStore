package com.techstore;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.techstore.shared.HealthController;
import org.junit.jupiter.api.Test;

class HealthControllerTest {

    @Test
    void healthReportsUp() {
        var status = new HealthController().health();
        assertEquals("UP", status.get("status"));
        assertEquals("techstore-api", status.get("service"));
    }
}
