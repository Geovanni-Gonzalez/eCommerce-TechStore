package com.techstore.users.dto;

/** Respuesta OpenAPI: no se serializan contraseñas, hashes ni sesiones internas. */
public record AuthSessionResponse(
        String accessToken,
        String tokenType,
        RegisteredUserResponse user
) {
}
