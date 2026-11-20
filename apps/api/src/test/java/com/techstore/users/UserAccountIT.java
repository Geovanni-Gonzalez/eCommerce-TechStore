package com.techstore.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.techstore.users.domain.UserAccount;
import com.techstore.users.domain.UserEmail;
import com.techstore.users.domain.UserRole;
import com.techstore.users.repository.UserAccountRepository;
import com.techstore.users.security.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flyway V2 y persistencia JDBC real sobre techstore_test.
 * Rollback garantiza que no queden usuarios de pruebas almacenados.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class UserAccountIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserAccountRepository repository;

    @Autowired
    private PasswordHasher hasher;

    @BeforeEach
    void assertIsolatedDatabase() {
        assertEquals("techstore_test",
                jdbc.queryForObject("SELECT current_database()", String.class),
                "No ejecutar pruebas de escritura fuera de techstore_test");
        jdbc.update("DELETE FROM users WHERE email LIKE 'it.%@techstore.test'");
    }

    @Test
    void clientCreatedAndRetrievedWithBcryptHashAndTimestamps() {
        String raw = "SoloPrueba#2026";
        var email = new UserEmail("  IT.CLIENT@TECHSTORE.TEST ");
        var passwordHash = hasher.hash(raw);

        UserAccount saved = repository.createClient("Cliente de prueba", email, passwordHash);

        assertNotNull(saved.id());
        assertEquals("it.client@techstore.test", saved.email().value());
        assertEquals("Cliente de prueba", saved.displayName());
        assertEquals(UserRole.CLIENTE, saved.role());
        assertTrue(saved.active());
        assertNotNull(saved.createdAt());
        assertNotNull(saved.updatedAt());
        assertNotEquals(raw, saved.passwordHash().value());
        assertTrue(hasher.matches(raw, saved.passwordHash()));
        assertEquals(saved, repository.findByEmail(
                new UserEmail("It.Client@TechStore.Test")).orElseThrow());
        assertEquals(saved, repository.findById(saved.id()).orElseThrow());
    }

    @Test
    void duplicateEmailIsRejectedRegardlessOfOriginalCase() {
        var hash = hasher.hash("SoloPrueba#2026");
        repository.createClient("Cliente duplicado", new UserEmail("IT.DUP@TECHSTORE.TEST"), hash);

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.createClient(
                        "Otro nombre", new UserEmail("it.dup@techstore.test"), hash));
    }

    @Test
    void databaseRejectsPlaintextPassword() {
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("""
                        INSERT INTO users (email, password_hash)
                        VALUES (?, ?)
                        """, "it.plain@techstore.test", "texto-plano"));
    }

    @Test
    void databaseRejectsUnknownRole() {
        var hash = hasher.hash("SoloPrueba#2026");
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("""
                        INSERT INTO users (email, password_hash, role)
                        VALUES (?, ?, 'VISITANTE')
                        """, "it.unknown@techstore.test", hash.value()));
    }

    @Test
    void adminRoleIsSupportedButNeverPassedToCreateClient() {
        var hash = hasher.hash("SoloPrueba#2026");
        Long id = jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, role)
                VALUES (?, ?, 'ADMIN')
                RETURNING id
                """, Long.class, "it.admin@techstore.test", hash.value());

        assertNotNull(id);
        assertEquals(UserRole.ADMIN, repository.findById(id).orElseThrow().role());
        assertFalse(repository.findById(-1).isPresent());
    }

    @Test
    void databaseRequiresNormalizedEmail() {
        var hash = hasher.hash("SoloPrueba#2026");
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("""
                        INSERT INTO users (email, password_hash)
                        VALUES (?, ?)
                        """, "IT.UPPER@TECHSTORE.TEST", hash.value()));
    }

    @Test
    void unknownAccountReturnsEmptyOptional() {
        assertTrue(repository.findByEmail(
                new UserEmail("it.nonexistent@techstore.test")).isEmpty());
        assertTrue(repository.findById(Long.MAX_VALUE).isEmpty());
    }
}
