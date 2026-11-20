package com.techstore.users.domain;

/**
 * Roles admitidos por BKL-014. El alta pública siempre será CLIENTE;
 * la administración de cuentas privilegiadas será una operación separada.
 */
public enum UserRole {
    CLIENTE,
    ADMIN
}
