package com.techstore.users.repository;

import com.techstore.users.domain.PasswordHash;
import com.techstore.users.domain.UserAccount;
import com.techstore.users.domain.UserEmail;
import java.util.Optional;

/**
 * Contrato de persistencia para BKL-014 y futuros casos de uso.
 * El alta pública solo permite el rol CLIENTE; ADMIN se gestiona aparte.
 */
public interface UserAccountRepository {

    Optional<UserAccount> findByEmail(UserEmail email);

    Optional<UserAccount> findById(long id);

    UserAccount createClient(String displayName, UserEmail email, PasswordHash passwordHash);
}
