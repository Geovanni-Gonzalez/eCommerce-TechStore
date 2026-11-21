package com.techstore.payments;

/** created=false significa replay: prohibido invocar otra vez al proveedor. */
public record PaymentAttemptClaim(PaymentAttemptSnapshot attempt, boolean created) { }
