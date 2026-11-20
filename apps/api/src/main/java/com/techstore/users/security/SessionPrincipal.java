package com.techstore.users.security;

import com.techstore.users.domain.UserRole;

/**
 * Principal seguro: no guarda credenciales, hashes ni el token Bearer.
 */
public record SessionPrincipal(long id, String email, String displayName, UserRole role) {
    @Override
    public String toString() {
        return "SessionPrincipal[id=" + id + ",role=" + role + "]";
    }
}
