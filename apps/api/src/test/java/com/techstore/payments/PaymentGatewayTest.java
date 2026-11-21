package com.techstore.payments;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentGatewayTest {
    private static PaymentAuthorizationRequest request() {
        return new PaymentAuthorizationRequest(
                7L, new BigDecimal("12500.00"), "attempt-2026-0001");
    }

    @Test
    void disabledByDefaultFailsClosedWithoutApprovingAnything() {
        PaymentGateway gateway = PaymentGatewayConfig.selectProvider(
                "DISABLED", false, "APPROVED");
        assertInstanceOf(DisabledPaymentGateway.class, gateway);
        assertThrows(PaymentGatewayUnavailableException.class,
                () -> gateway.authorize(request()));
    }

    @Test
    void sandboxRequiresExplicitOptInEvenForDeclines() {
        assertThrows(IllegalArgumentException.class,
                () -> PaymentGatewayConfig.selectProvider("SANDBOX", false, "DECLINED"));
        assertThrows(IllegalArgumentException.class,
                () -> PaymentGatewayConfig.selectProvider(null, true, "DECLINED"));
        assertThrows(IllegalArgumentException.class,
                () -> PaymentGatewayConfig.selectProvider("LIVE", true, "APPROVED"));
        assertThrows(IllegalArgumentException.class,
                () -> PaymentGatewayConfig.selectProvider("SANDBOX", true, "SUCCESS"));
    }

    @Test
    void deterministicSandboxApprovedProducesReferenceWithoutRealCharge() {
        PaymentGateway gateway = PaymentGatewayConfig.selectProvider(
                "sandbox", true, "approved");
        PaymentAuthorization one = gateway.authorize(request());
        PaymentAuthorization two = gateway.authorize(request());
        assertEquals(PaymentOutcome.APPROVED, one.outcome());
        assertEquals(one, two);
        assertTrue(one.providerReference().startsWith("SBX-"));
        assertFalse(one.providerReference().contains("attempt-"));
    }

    @Test
    void deterministicSandboxDeclineDoesNotApprove() {
        PaymentGateway gateway = PaymentGatewayConfig.selectProvider(
                "SANDBOX", true, "DECLINED");
        PaymentAuthorization result = gateway.authorize(request());
        assertEquals(PaymentOutcome.DECLINED, result.outcome());
        assertTrue(result.providerReference().startsWith("SBX-"));
    }

    @Test
    void differentOrderAmountAndAttemptYieldDifferentReferences() {
        PaymentGateway gateway = new SandboxPaymentGateway(PaymentOutcome.APPROVED);
        var original = gateway.authorize(request());
        var changedOrder = gateway.authorize(new PaymentAuthorizationRequest(
                8, new BigDecimal("12500.00"), "attempt-2026-0001"));
        var changedAmount = gateway.authorize(new PaymentAuthorizationRequest(
                7, new BigDecimal("12501.00"), "attempt-2026-0001"));
        var changedAttempt = gateway.authorize(new PaymentAuthorizationRequest(
                7, new BigDecimal("12500.00"), "attempt-2026-0002"));
        assertNotEquals(original.providerReference(), changedOrder.providerReference());
        assertNotEquals(original.providerReference(), changedAmount.providerReference());
        assertNotEquals(original.providerReference(), changedAttempt.providerReference());
    }

    @Test
    void requestRejectsForgedOrMalformedAmountsAndKeys() {
        assertThrows(IllegalArgumentException.class,
                () -> new PaymentAuthorizationRequest(0,
                        new BigDecimal("12500"), "attempt-2026-0001"));
        assertThrows(IllegalArgumentException.class,
                () -> new PaymentAuthorizationRequest(1,
                        new BigDecimal("-1"), "attempt-2026-0001"));
        assertThrows(IllegalArgumentException.class,
                () -> new PaymentAuthorizationRequest(1,
                        new BigDecimal("1.001"), "attempt-2026-0001"));
        assertThrows(IllegalArgumentException.class,
                () -> new PaymentAuthorizationRequest(1,
                        BigDecimal.ZERO, "attempt-2026-0001"));
        for (String key : new String[] {"", "abc", "key with spaces",
                "invalid/key", "x".repeat(81)}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new PaymentAuthorizationRequest(1, BigDecimal.ONE, key));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new PaymentAuthorizationRequest(1, BigDecimal.ONE, null));
    }

    @Test
    void requestNormalizesCrcAndRejectsImpossibleResult() {
        var one = new PaymentAuthorizationRequest(7,
                new BigDecimal("200"), "attempt-2026-0001");
        var two = new PaymentAuthorizationRequest(7,
                new BigDecimal("200.00"), "attempt-2026-0001");
        assertEquals(one, two);
        assertThrows(IllegalArgumentException.class,
                () -> new PaymentAuthorization(PaymentOutcome.APPROVED, "abc"));
        assertThrows(NullPointerException.class,
                () -> new PaymentAuthorization(null, "SBX-12345678"));
    }

    @Test
    void gatewayCannotBeCalledWithNullRequest() {
        assertThrows(NullPointerException.class,
                () -> new SandboxPaymentGateway(PaymentOutcome.APPROVED).authorize(null));
        assertThrows(NullPointerException.class,
                () -> new DisabledPaymentGateway().authorize(null));
    }
}
