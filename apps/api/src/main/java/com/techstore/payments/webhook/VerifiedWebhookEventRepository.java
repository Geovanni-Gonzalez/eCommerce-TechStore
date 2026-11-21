package com.techstore.payments.webhook;

/** Persistencia atómica para notificaciones válidas y su deduplicación. */
public interface VerifiedWebhookEventRepository {
    WebhookEventReceipt record(VerifiedSandboxWebhookEvent event,
            String payloadSha256);
}
