package com.techstore.payments.sessions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Origen de confianza fijo, token no predecible y no persistido en claro. */
class SandboxSessionTokenTest {
    @Test
    void tokenHas256BitsAndOnlyHashIsStored() {
        String one = SandboxSessionToken.generate();
        String two = SandboxSessionToken.generate();
        assertEquals(43, one.length());
        assertEquals(43, two.length());
        assertNotEquals(one, two);
        assertNotEquals(SandboxSessionToken.hash(one), SandboxSessionToken.hash(two));
        assertEquals(SandboxSessionToken.hash(one), SandboxSessionToken.hash(one));
        assertEquals(64, SandboxSessionToken.hash(one).length());
        assertThrows(SandboxSessionUnavailableException.class,
                () -> SandboxSessionToken.hash("abc"));
        assertThrows(SandboxSessionUnavailableException.class,
                () -> SandboxSessionToken.hash(null));
    }

    @Test
    void strictRedirectPolicyNeverAcceptsReturnUrlFromRequest() {
        var policy = new SandboxCheckoutRedirectPolicy("http://localhost:3000",
                "http://localhost:3000,http://127.0.0.1:3000");
        String token = SandboxSessionToken.generate();
        assertEquals("http://localhost:3000/pagos/sandbox#session=" + token,
                policy.redirectUrl(token));
        for (String origin : new String[]{
                "https://phishing.example", "http://evil.example",
                "http://localhost:3000@evil.example",
                "http://localhost:3000/?redirect=https://evil.example",
                "http://localhost:3000/#admin", "http://localhost:3000/path",
                "javascript:alert(1)", "file:///etc/passwd",
                "http://localhost:3001", "http://127.0.0.1:3000",
                "http://localhost:3000.evil.test"
        }) {
            assertThrows(IllegalArgumentException.class,
                    () -> new SandboxCheckoutRedirectPolicy(origin,
                            "http://localhost:3000"), origin);
        }
        assertThrows(SandboxSessionUnavailableException.class,
                () -> policy.redirectUrl("invalid-token"));
    }

    @Test
    void explicitlyAllowedHttpsOriginIsAccepted() {
        var policy = new SandboxCheckoutRedirectPolicy("https://store.example.test",
                "https://store.example.test");
        assertTrue(policy.redirectUrl(SandboxSessionToken.generate())
                .startsWith("https://store.example.test/pagos/sandbox#session="));
    }
}
