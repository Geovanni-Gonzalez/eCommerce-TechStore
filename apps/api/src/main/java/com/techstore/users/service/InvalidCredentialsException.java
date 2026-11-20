package com.techstore.users.service;

/**
 * Mensaje idéntico para cuenta inexistente, clave errónea o cuenta desactivada.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Correo o contraseña incorrectos");
    }
}
