package com.techstore.users.security;

import com.techstore.users.repository.SessionTokenRepository;
import com.techstore.users.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Política de mínimo privilegio para toda la API.
 *
 * No hay cookies de sesión, HTTP Basic ni login por formulario. Los clientes
 * envían Authorization: Bearer <token> en cada solicitud privada. El esquema
 * es opaco y revocable, NO JWT.
 */
@Configuration
public class ApiSecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, SessionTokenRepository sessions,
            UserAccountRepository users, AccessTokenCodec codec) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> {})
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, failure) ->
                                writeProblem(response, 401, "No autenticado",
                                        "Se requiere un token Bearer válido",
                                        "AUTHENTICATION_REQUIRED"))
                        .accessDeniedHandler((request, response, failure) ->
                                writeProblem(response, 403, "Acceso denegado",
                                        "No tienes permiso para acceder a este recurso",
                                        "ACCESS_DENIED")))
                .authorizeHttpRequests(access -> access
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/health", "/actuator/health",
                                "/api/products", "/api/products/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/webhooks/sandbox")
                            .permitAll() // HMAC y timestamp son autenticación del proveedor
                        // BKL-042: los listados y detalles de CLIENT nunca son públicos.
                        .requestMatchers(HttpMethod.GET,
                                "/api/orders", "/api/orders/*",
                                "/api/orders/*/history").hasRole("CLIENT")
                        .requestMatchers(HttpMethod.POST, "/api/orders").hasRole("CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/orders/*/shipping").hasRole("CLIENT")
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/cancel").hasRole("CLIENT")
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/payment-sessions")
                            .hasRole("CLIENT")
                        .requestMatchers(HttpMethod.POST,
                                "/api/payments/sandbox/sessions/preview",
                                "/api/payments/sandbox/sessions/complete")
                            .permitAll() // único acceso con token de 256 bits, sin cookie
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/payment-attempts")
                            .hasRole("CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/orders/*/payment-attempts/*")
                            .hasRole("CLIENT")
                        // BKL-051: solo el listado de reseñas es público.
                        .requestMatchers(HttpMethod.GET, "/api/reviews/products/*")
                            .permitAll()
                        // BKL-050: reseñas solo para propietario CLIENT con entrega real.
                        .requestMatchers(HttpMethod.GET,
                                "/api/reviews/products/*/eligibility",
                                "/api/reviews/products/*/mine").hasRole("CLIENT")
                        .requestMatchers(HttpMethod.POST, "/api/reviews/products/*")
                            .hasRole("CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/checkout/quote").hasRole("CLIENT")
                        .requestMatchers("/api/cart", "/api/cart/**").hasRole("CLIENT")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(
                        new BearerTokenAuthenticationFilter(sessions, users, codec),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    // Evita que Spring Boot genere y escriba una contraseña temporal en logs:
    // las cuentas se comprueban exclusivamente mediante AuthenticationService.
    @Bean
    UserDetailsService unusedFormLoginService() {
        return username -> {
            throw new UsernameNotFoundException("Login HTTP Basic deshabilitado");
        };
    }

    private static void writeProblem(
            HttpServletResponse response, int status, String title,
            String detail, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(
                "{\"type\":\"about:blank\",\"title\":\"" + title
                + "\",\"status\":" + status + ",\"detail\":\"" + detail
                + "\",\"code\":\"" + code + "\"}");
    }
}
