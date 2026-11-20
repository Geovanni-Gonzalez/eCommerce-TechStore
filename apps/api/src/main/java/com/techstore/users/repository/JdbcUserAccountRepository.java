package com.techstore.users.repository;

import com.techstore.users.domain.PasswordHash;
import com.techstore.users.domain.UserAccount;
import com.techstore.users.domain.UserEmail;
import com.techstore.users.domain.UserRole;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Única implementación JDBC del contrato de cuentas.
 * No recibe contraseñas planas: solo acepta el valor de dominio PasswordHash.
 */
@Repository
public class JdbcUserAccountRepository implements UserAccountRepository {

    private static final String FIELDS = """
            id, display_name, email, password_hash, role, active, created_at, updated_at
            """;

    private static final RowMapper<UserAccount> MAPPER =
            JdbcUserAccountRepository::mapAccount;

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public Optional<UserAccount> findByEmail(UserEmail email) {
        Objects.requireNonNull(email, "email");
        List<UserAccount> users = jdbcTemplate.query(
                "SELECT " + FIELDS + " FROM users WHERE email = ?",
                MAPPER, email.value());
        return users.stream().findFirst();
    }

    @Override
    public Optional<UserAccount> findById(long id) {
        if (id < 1) {
            return Optional.empty();
        }
        List<UserAccount> users = jdbcTemplate.query(
                "SELECT " + FIELDS + " FROM users WHERE id = ?",
                MAPPER, id);
        return users.stream().findFirst();
    }

    @Override
    public UserAccount createClient(
            String displayName, UserEmail email, PasswordHash passwordHash) {
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(passwordHash, "passwordHash");

        // No se acepta un rol externo en el flujo de alta.
        // La restricción UNIQUE en PostgreSQL resuelve duplicados concurrentes.
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                "INSERT INTO users (display_name, email, password_hash, role) "
                        + "VALUES (?, ?, ?, 'CLIENTE') RETURNING " + FIELDS,
                MAPPER,
                displayName, email.value(), passwordHash.value()));
    }

    private static UserAccount mapAccount(ResultSet rs, int rowNum)
            throws SQLException {
        return new UserAccount(
                rs.getLong("id"),
                rs.getString("display_name"),
                new UserEmail(rs.getString("email")),
                new PasswordHash(rs.getString("password_hash")),
                UserRole.valueOf(rs.getString("role")),
                rs.getBoolean("active"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
