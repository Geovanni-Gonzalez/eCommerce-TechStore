package com.techstore.users.controller;

import com.techstore.users.domain.UserRole;
import com.techstore.users.dto.AuthSessionResponse;
import com.techstore.users.dto.LoginRequest;
import com.techstore.users.dto.RegisteredUserResponse;
import com.techstore.users.security.SessionPrincipal;
import com.techstore.users.service.AuthenticationService;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador HTTP que no conoce BCrypt, SQL ni almacenamiento de tokens.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping(value = "/login",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthSessionResponse> login(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(authenticationService.login(
                LoginRequest.fromJsonMap(body)));
    }

    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public RegisteredUserResponse currentUser(
            @AuthenticationPrincipal SessionPrincipal principal) {
        String role = principal.role() == UserRole.ADMIN ? "ADMIN" : "CLIENT";
        return new RegisteredUserResponse(
                principal.id(), principal.displayName(), principal.email(), role);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authorization) {
        authenticationService.logout(authorization.substring("Bearer ".length()));
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }
}
