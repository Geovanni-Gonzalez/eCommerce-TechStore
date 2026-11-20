package com.techstore.catalog.controller;

/**
 * Oculta deliberadamente si un identificador no existe o está inactivo.
 * Ambos casos deben ser HTTP 404 en el catálogo público.
 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException() {
        super("No se encontró un producto activo con ese identificador");
    }
}
