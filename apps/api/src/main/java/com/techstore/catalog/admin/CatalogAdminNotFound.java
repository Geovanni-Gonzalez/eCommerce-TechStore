package com.techstore.catalog.admin;

/** Evita filtrar detalles internos en recursos ADMIN inexistentes. */
public final class CatalogAdminNotFound extends RuntimeException {
    public CatalogAdminNotFound() { super("Recurso administrativo no encontrado"); }
}
