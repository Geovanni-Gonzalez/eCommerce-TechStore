package com.techstore.users;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.techstore.users.domain.PasswordHash;
import com.techstore.users.domain.UserAccount;
import com.techstore.users.domain.UserEmail;
import com.techstore.users.domain.UserRole;
import com.techstore.users.security.BcryptPasswordHasher;
import org.junit.jupiter.api.Test;

class UserDomainTest {

    @Test
    void emailNormalizesWhitespaceAndCase() {
        assertEquals("cliente@techstore.test",
                new UserEmail("  CLIENTE@TECHSTORE.TEST  ").value());
    }

    @Test
    void emailRejectsBlankMissingAtAndSpaces() {
        assertThrows(IllegalArgumentException.class,
                () -> new UserEmail(" "));
        assertThrows(IllegalArgumentException.class,
                () -> new UserEmail("no-es-un-correo"));
        assertThrows(IllegalArgumentException.class,
                () -> new UserEmail("nombre con espacio@test.com"));
    }

    @Test
    void onlyTwoUserRolesExist() {
        assertArrayEquals(new UserRole[]{UserRole.CLIENTE, UserRole.ADMIN},
                UserRole.values());
    }

    @Test
    void bcryptHashesAndVerifiesWithoutStoringPlaintext() {
        var hasher = new BcryptPasswordHasher();
        String examplePassword = "EjemploDePrueba#2026";

        PasswordHash first = hasher.hash(examplePassword);
        PasswordHash second = hasher.hash(examplePassword);

        assertNotEquals(examplePassword, first.value());
        assertNotEquals(first.value(), second.value(),
                "BCrypt debe producir salts aleatorios");
        assertTrue(hasher.matches(examplePassword, first));
        assertTrue(hasher.matches(examplePassword, second));
        assertFalse(hasher.matches("incorrecto", first));
        assertFalse(hasher.matches(null, first));
    }

    @Test
    void plaintextIsRejectedWhenConstructingPasswordHash() {
        assertThrows(IllegalArgumentException.class,
                () -> new PasswordHash("clave-en-texto-plano"));
    }

    @Test
    void passwordHashAndUserAccountToStringDoNotRevealSecrets() {
        var hash = new BcryptPasswordHasher().hash("EjemploDePrueba#2026");
        var account = new UserAccount(
                1L, "Cliente", new UserEmail("cliente@techstore.test"),
                hash, UserRole.CLIENTE, true, null, null);

        assertEquals("[REDACTED]", hash.toString());
        assertFalse(account.toString().contains(hash.value()));
        assertFalse(account.toString().contains("EjemploDePrueba"));
    }
}
