package com.techstore.payments.sessions;

/** Persistencia de token de redirección y su canje atómico. */
public interface SandboxSessionRepository {
    SandboxSessionSnapshot issue(long orderId, long attemptId, String tokenHash);
    SandboxSessionSnapshot preview(String tokenHash);
    SandboxSessionSnapshot redeem(String tokenHash);
}
