package com.techstore.users.service;

/**
 * Respuesta neutra para el caso de correo duplicado (HTTP 409).
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("Ya existe una cuenta con ese correo electrónico");
    }
}
