package com.techstore.payments.sessions;

/** Token inválido, caducado o consumido. Evita exponer un pedido ajeno. */
public final class SandboxSessionUnavailableException extends RuntimeException { }
