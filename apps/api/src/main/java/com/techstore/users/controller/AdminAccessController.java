package com.techstore.users.controller;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prueba funcional del acceso administrativo. No es todavía un panel de
 * gestión; sus operaciones concretas pertenecen al módulo de administración.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminAccessController {

    @GetMapping(value = "/check", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> check() {
        return Map.of("status", "AUTHORIZED", "role", "ADMIN");
    }
}
