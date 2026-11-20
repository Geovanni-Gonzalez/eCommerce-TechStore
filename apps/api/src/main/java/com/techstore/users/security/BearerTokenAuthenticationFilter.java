package com.techstore.users.security;

import com.techstore.users.domain.UserAccount;
import com.techstore.users.domain.UserRole;
import com.techstore.users.repository.SessionTokenRepository;
import com.techstore.users.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica cada solicitud con un Bearer opaco vigente y no revocado.
 * Nunca escribe ni registra el token, y vuelve a leer rol/estado de la cuenta
 * para aplicar desactivaciones y cambios de permisos inmediatamente.
 */
public final class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private final SessionTokenRepository sessions;
    private final UserAccountRepository users;
    private final AccessTokenCodec codec;

    public BearerTokenAuthenticationFilter(
            SessionTokenRepository sessions, UserAccountRepository users,
            AccessTokenCodec codec) {
        this.sessions = Objects.requireNonNull(sessions);
        this.users = Objects.requireNonNull(users);
        this.codec = Objects.requireNonNull(codec);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring("Bearer ".length());
            if (codec.isWellFormed(token)) {
                sessions.findActiveUserId(codec.hash(token))
                        .flatMap(users::findById)
                        .filter(UserAccount::active)
                        .ifPresent(user -> {
                            String authority = user.role() == UserRole.ADMIN
                                    ? "ROLE_ADMIN" : "ROLE_CLIENT";
                            var principal = new SessionPrincipal(
                                    user.id(), user.email().value(),
                                    user.displayName(), user.role());
                            var authentication = new UsernamePasswordAuthenticationToken(
                                    principal, null,
                                    List.of(new SimpleGrantedAuthority(authority)));
                            SecurityContextHolder.getContext()
                                    .setAuthentication(authentication);
                        });
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
