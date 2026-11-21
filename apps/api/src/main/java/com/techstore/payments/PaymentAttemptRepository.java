package com.techstore.payments;

/** Puerto durable con transacciones propias, separado del gateway. */
public interface PaymentAttemptRepository {
    PaymentAttemptClaim claim(long userId, long orderId, String keyHash);
    PaymentAttemptSnapshot complete(long attemptId, PaymentAttemptStatus status,
            String providerReference);
    PaymentAttemptSnapshot findForOwner(long userId, long orderId, long attemptId);
}
