package com.techstore.orders.payments;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.techstore.cart.repository.CartRepository;
import com.techstore.orders.domain.Order;
import com.techstore.orders.domain.ShippingAddress;
import com.techstore.orders.service.CheckoutFacade;
import com.techstore.orders.service.OrderNotCancellableException;
import com.techstore.orders.service.OrderReservationService;
import com.techstore.payments.PaymentAttemptRepository;
import com.techstore.payments.PaymentAttemptService;
import com.techstore.payments.PaymentAttemptStatus;
import com.techstore.payments.PaymentGatewayUnavailableException;
import com.techstore.payments.PaymentOutcome;
import com.techstore.payments.SandboxPaymentGateway;
import com.techstore.payments.sessions.SandboxCheckoutSessionService;
import com.techstore.payments.sessions.SandboxSessionRepository;
import com.techstore.payments.sessions.SandboxSessionToken;
import com.techstore.users.security.PasswordHasher;
import jakarta.servlet.Filter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * BKL-041 con commits PostgreSQL reales; sin dinero real.
 * Un STARTED/UNKNOWN se mantiene retenido si NO hay prueba de no autorización.
 */
@SpringBootTest(properties={
        "techstore.payment.provider=SANDBOX",
        "techstore.payment.sandbox.enabled=true",
        "techstore.payment.sandbox.outcome=DECLINED"
})
@ActiveProfiles("integration")
class PaymentExposureLifecycleIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired CartRepository carts;
    @Autowired CheckoutFacade checkout;
    @Autowired PaymentAttemptRepository attempts;
    @Autowired SandboxCheckoutSessionService hosted;
    @Autowired SandboxSessionRepository checkoutSessions;
    @Autowired OrderReservationService lifecycle;
    @Autowired PaymentIncidentRepository incidents;
    @Autowired PasswordHasher passwords;
    @Autowired WebApplicationContext context;

    private MockMvc mvc;
    private long userId, productId, orderId;
    private final List<Long> authUsers = new ArrayList<>();

    @BeforeEach
    void setup() {
        assertEquals("techstore_test", jdbc.queryForObject(
                "SELECT current_database()", String.class));
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain",Filter.class))
                .build();
        String suffix=UUID.randomUUID().toString().substring(0,12);
        userId=jdbc.queryForObject("""
                INSERT INTO users(display_name,email,password_hash,role)
                VALUES('Conciliación',?,?, 'CLIENTE') RETURNING id
                """, Long.class, "it.payment.exposure."+suffix+"@techstore.test",
                "$2a$12$"+"A".repeat(53));
        productId=jdbc.queryForObject("""
                INSERT INTO products(sku,name,price_crc,stock,active)
                VALUES(?, 'Reserva segura',1500.00,5,TRUE) RETURNING id
                """, Long.class,"EXP-"+suffix);
        carts.ensureCart(userId);
        assertTrue(carts.incrementProduct(userId, productId, 2));
        Order order=checkout.confirm(userId,new ShippingAddress(
                "San José","Central","Carmen","Casa junto al parque",
                "Ana Pérez","8888-1111"));
        orderId=order.id();
    }

    @Test
    void noPaymentAtExpiryReleasesOnlyOnce() {
        expireReservation();
        assertTrue(lifecycle.expireOne(orderId));
        assertFalse(lifecycle.expireOne(orderId));
        assertEquals("CANCELLED",orderStatus());
        assertEquals("RELEASED",reservationStatus());
        assertEquals(5,stock());
        assertTrue(incidents.findForOrder(orderId).isEmpty());
    }

    @Test
    void verifiedLocalDeclineAndUnavailableCanExpireWithoutCharging() {
        var service=new PaymentAttemptService(attempts,
                new SandboxPaymentGateway(PaymentOutcome.DECLINED));
        var attempt=service.submit(userId,orderId,"key-declined-0001").attempt();
        assertEquals(PaymentAttemptStatus.SIMULATED_DECLINED,attempt.status());
        expireReservation();
        assertTrue(lifecycle.expireOne(orderId));
        assertEquals("RELEASED",reservationStatus());
        assertEquals(5,stock());
    }

    @Test
    void unavailableGatewayMayExpireOnlyWhenNoAuthorizationWasAttempted() {
        var service=new PaymentAttemptService(attempts,
                request -> { throw new PaymentGatewayUnavailableException(); });
        assertEquals(PaymentAttemptStatus.UNAVAILABLE,
                service.submit(userId,orderId,"key-unavailable-0001")
                    .attempt().status());
        expireReservation();
        assertTrue(lifecycle.expireOne(orderId));
        assertEquals(5,stock());
    }

    @Test
    void directStartedClaimIsAmbiguousAndMustNotReleaseOrAllowCancellation() {
        attempts.claim(userId,orderId,"A".repeat(64));
        assertThrows(OrderNotCancellableException.class,
                () -> lifecycle.cancelOwned(userId,orderId));
        expireReservation();
        assertFalse(lifecycle.expireOne(orderId));
        assertFalse(lifecycle.expireOne(orderId));
        assertEquals("PENDING",orderStatus());
        assertEquals("RESERVED",reservationStatus());
        assertEquals(3,stock());
        assertEquals(1,incidents.findForOrder(orderId).size());
        assertEquals(PaymentIncidentReason.AMBIGUOUS_AUTHORIZATION,
                incidents.findForOrder(orderId).getFirst().reason());
    }

    @Test
    void concurrentExpiryAttemptsKeepOneIncidentAndNeverReleaseUnknown()
            throws Exception {
        attempts.claim(userId,orderId,"D".repeat(64));
        expireReservation();
        var ready=new java.util.concurrent.CountDownLatch(2);
        var go=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            java.util.concurrent.Callable<Boolean> task=() -> {
                ready.countDown();
                assertTrue(go.await(10,java.util.concurrent.TimeUnit.SECONDS));
                return lifecycle.expireOne(orderId);
            };
            var one=pool.submit(task);
            var two=pool.submit(task);
            assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS));
            go.countDown();
            assertFalse(one.get(25,java.util.concurrent.TimeUnit.SECONDS));
            assertFalse(two.get(25,java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(1,incidents.findForOrder(orderId).size());
        assertEquals("PENDING",orderStatus());
        assertEquals("RESERVED",reservationStatus());
        assertEquals(3,stock());
    }

    @Test
    void unknownAuthorizationHoldsExpiredStockForHumanInvestigation() {
        var attempt=attempts.claim(userId,orderId,"B".repeat(64)).attempt();
        attempts.complete(attempt.id(),PaymentAttemptStatus.UNKNOWN,null);
        expireReservation();
        assertFalse(lifecycle.expireOne(orderId));
        assertThrows(OrderNotCancellableException.class,
                () -> lifecycle.cancelOwned(userId,orderId));
        assertEquals("AMBIGUOUS_AUTHORIZATION",incidentReason());
        assertEquals("RESERVED",reservationStatus());
        assertEquals(3,stock());
    }

    @Test
    void unredeemedHostedSessionIsProvenNotAuthorizedAndMayExpire() {
        var session=hosted.start(userId,orderId,
                "key-hosted-unredeemed-0001");
        assertTrue(session.response().redirectUrl().contains("#session="));
        expireReservation();
        assertTrue(lifecycle.expireOne(orderId));
        assertEquals("CANCELLED",orderStatus());
        assertEquals("RELEASED",reservationStatus());
        assertEquals(5,stock());
        assertTrue(incidents.findForOrder(orderId).isEmpty());
    }

    @Test
    void redeemedHostedTokenWithStartedAttemptIsAmbiguousUntilReconciled() {
        var session=hosted.start(userId,orderId,
                "key-hosted-redeemed-0001");
        String token=session.response().redirectUrl().split("#session=",2)[1];
        var redeemed=checkoutSessions.redeem(SandboxSessionToken.hash(token));
        assertEquals(orderId,redeemed.orderId());
        // Simula caída de proceso después de canje pero antes del resultado.
        expireReservation();
        assertFalse(lifecycle.expireOne(orderId));
        assertEquals("AMBIGUOUS_AUTHORIZATION",incidentReason());
        assertEquals("RESERVED",reservationStatus());
        assertEquals(3,stock());
    }

    @Test
    void approvedSandboxAttemptWaitsForSignedReconciliationAndHoldsStock() {
        var response=new PaymentAttemptService(attempts,
                new SandboxPaymentGateway(PaymentOutcome.APPROVED))
                .submit(userId,orderId,"key-approved-0001").attempt();
        assertEquals(PaymentAttemptStatus.SIMULATED_APPROVED,response.status());
        assertThrows(OrderNotCancellableException.class,
                () -> lifecycle.cancelOwned(userId,orderId));
        expireReservation();
        assertFalse(lifecycle.expireOne(orderId));
        assertEquals("AWAITING_VERIFIED_APPROVAL",incidentReason());
        assertEquals("PENDING",orderStatus());
        assertEquals("RESERVED",reservationStatus());
        assertEquals(3,stock());
    }

    @Test
    void adminCanReadIncidentsButCannotForcePaymentResolution() throws Exception {
        attempts.claim(userId,orderId,"C".repeat(64));
        expireReservation();
        assertFalse(lifecycle.expireOne(orderId));
        String url="/api/admin/orders/"+orderId+"/payment-incidents";
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        String client=tokenFor("CLIENTE");
        mvc.perform(get(url).header("Authorization","Bearer "+client))
                .andExpect(status().isForbidden());
        String admin=tokenFor("ADMIN");
        mvc.perform(get(url).header("Authorization","Bearer "+admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(orderId))
                .andExpect(jsonPath("$[0].reason").value("AMBIGUOUS_AUTHORIZATION"))
                .andExpect(jsonPath("$[0].webhookEventId").isEmpty());
        mvc.perform(get("/api/admin/orders/99999999/payment-incidents")
                .header("Authorization","Bearer "+admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_INCIDENT_ORDER_NOT_FOUND"));
    }

    private String tokenFor(String role) throws Exception {
        String email="it.exposure.auth."+UUID.randomUUID()+"@techstore.test";
        Long id=jdbc.queryForObject("""
                INSERT INTO users(display_name,email,password_hash,role)
                VALUES(?,?,?,?) RETURNING id
                """,Long.class,"Usuario conciliación",email,
                passwords.hash("ClaveDePrueba2026").value(),role);
        authUsers.add(id);
        var result=mvc.perform(post("/api/auth/login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"email\":\""+email+"\",\"password\":\"ClaveDePrueba2026\"}"))
                .andExpect(status().isOk()).andReturn();
        return tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private void expireReservation() {
        jdbc.update("""
                UPDATE stock_reservations
                SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 minute'
                WHERE order_id=?
                """,orderId);
    }
    private String incidentReason() {
        return incidents.findForOrder(orderId).getFirst().reason().name();
    }
    private String orderStatus() {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id=?",
                String.class,orderId);
    }
    private String reservationStatus() {
        return jdbc.queryForObject("""
                SELECT status FROM stock_reservations WHERE order_id=? LIMIT 1
                """,String.class,orderId);
    }
    private int stock() {
        return jdbc.queryForObject("SELECT stock FROM products WHERE id=?",
                Integer.class,productId);
    }

    @AfterEach
    void cleanup() {
        if(userId<1)return;
        jdbc.update("DELETE FROM payment_reconciliation_incidents WHERE order_id=?",orderId);
        jdbc.update("""
                DELETE FROM payment_webhook_events
                WHERE payment_attempt_id IN (
                    SELECT id FROM payment_attempts WHERE order_id=?)
                """,orderId);
        jdbc.update("""
                DELETE FROM sandbox_checkout_sessions
                WHERE payment_attempt_id IN (
                    SELECT id FROM payment_attempts WHERE order_id=?)
                """,orderId);
        jdbc.update("DELETE FROM payment_attempts WHERE order_id=?",orderId);
        jdbc.update("DELETE FROM order_status_history WHERE order_id=?",orderId);
        jdbc.update("DELETE FROM order_event_audit WHERE order_id=?",orderId);
        jdbc.update("DELETE FROM stock_reservations WHERE order_id=?",orderId);
        jdbc.update("DELETE FROM order_items WHERE order_id=?",orderId);
        jdbc.update("DELETE FROM orders WHERE id=?",orderId);
        jdbc.update("""
                DELETE FROM cart_items WHERE cart_id IN
                    (SELECT id FROM carts WHERE user_id=?)
                """,userId);
        jdbc.update("DELETE FROM carts WHERE user_id=?",userId);
        jdbc.update("DELETE FROM products WHERE id=?",productId);
        jdbc.update("DELETE FROM users WHERE id=?",userId);
        for(Long id:authUsers){
            jdbc.update("DELETE FROM auth_sessions WHERE user_id=?",id);
            jdbc.update("DELETE FROM users WHERE id=?",id);
        }
        authUsers.clear();
    }
}
