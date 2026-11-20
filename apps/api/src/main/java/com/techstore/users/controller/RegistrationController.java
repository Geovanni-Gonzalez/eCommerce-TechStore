package com.techstore.users.controller;

import com.techstore.users.dto.RegisterRequest;
import com.techstore.users.dto.RegisteredUserResponse;
import com.techstore.users.service.RegistrationService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST del registro público.
 * Se comprueban estrictamente los campos JSON (additionalProperties:false)
 * antes de delegar al servicio: nunca se acepta role desde el cliente.
 */
@RestController
@RequestMapping("/api/auth")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping(value = "/register",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RegisteredUserResponse> register(
            @RequestBody Map<String, Object> body) {
        RegisterRequest request = RegisterRequest.fromJsonMap(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registrationService.register(request));
    }
}
