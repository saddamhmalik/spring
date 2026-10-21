package com.example.notify.web;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final String secretKey;
    private final String webhookSecret;
    private final String currency;
    private final String successUrl;
    private final String cancelUrl;

    private final Map<String, Map<String, String>> webhookEvents = new ConcurrentHashMap<>();

    public PaymentController(@Value("${app.stripe.secret-key:}") String secretKey,
                             @Value("${app.stripe.webhook-secret:}") String webhookSecret,
                             @Value("${app.stripe.currency:usd}") String currency,
                             @Value("${app.stripe.success-url:http://localhost:8080/api/payments/success}") String successUrl,
                             @Value("${app.stripe.cancel-url:http://localhost:8080/api/payments/cancel}") String cancelUrl) {
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
        this.currency = currency;
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of(
                "configured", !secretKey.isBlank(),
                "webhookConfigured", !webhookSecret.isBlank(),
                "currency", currency,
                "eventsReceived", webhookEvents.size());
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(@Valid @RequestBody CheckoutRequest request)
            throws StripeException {
        if (secretKey.isBlank()) {
            return ResponseEntity.ok(new CheckoutResponse("skipped", null, null,
                    "Set STRIPE_SECRET_KEY (sk_test_...) to create a real checkout session."));
        }

        Stripe.apiKey = secretKey;
        log.info("Creating checkout session for {} at {} {}", request.productName(), request.amount(), currency);

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(cancelUrl)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(request.quantity())
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(currency)
                                .setUnitAmount(request.amount())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(request.productName())
                                        .build())
                                .build())
                        .build())
                .build();

        Session session = Session.create(params);

        return ResponseEntity.ok(new CheckoutResponse("created", session.getId(), session.getUrl(),
                "Checkout session created for " + request.productName()));
    }

    @GetMapping("/session/{id}")
    public ResponseEntity<Map<String, Object>> session(@PathVariable String id) throws StripeException {
        if (secretKey.isBlank()) {
            return ResponseEntity.ok(Map.of("status", "skipped",
                    "detail", "Set STRIPE_SECRET_KEY to retrieve a session."));
        }

        Stripe.apiKey = secretKey;
        Session session = Session.retrieve(id);

        return ResponseEntity.ok(Map.of(
                "id", session.getId(),
                "paymentStatus", String.valueOf(session.getPaymentStatus()),
                "status", String.valueOf(session.getStatus()),
                "amountTotal", session.getAmountTotal(),
                "currency", String.valueOf(session.getCurrency())));
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody String payload,
                                                       @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (webhookSecret.isBlank()) {
            log.warn("Stripe webhook received but no webhook secret configured");
            return ResponseEntity.ok(Map.of("received", false,
                    "detail", "Set STRIPE_WEBHOOK_SECRET to verify webhook signatures."));
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException exception) {
            log.warn("Rejected Stripe webhook with invalid signature", exception);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("received", false, "detail", "Invalid signature"));
        } catch (Exception exception) {
            log.error("Failed to parse Stripe webhook", exception);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("received", false, "detail", exception.getMessage()));
        }

        webhookEvents.put(event.getId(), Map.of(
                "type", event.getType(),
                "created", String.valueOf(event.getCreated())));

        log.info("Stripe webhook received: {} ({})", event.getType(), event.getId());
        return ResponseEntity.ok(Map.of("received", true, "type", event.getType(), "id", event.getId()));
    }

    @GetMapping("/webhook/events")
    public Map<String, Map<String, String>> webhookEvents() {
        return webhookEvents;
    }

    @GetMapping(value = "/success", produces = MediaType.TEXT_HTML_VALUE)
    public String success(@RequestParam(name = "session_id", required = false) String sessionId) {
        String link = sessionId == null
                ? ""
                : "<p><a href=\"/api/payments/session/" + sessionId + "\">View session status</a></p>";
        return page("Payment successful",
                "<p>Checkout completed.</p><p>Session: <code>"
                        + (sessionId == null ? "unknown" : sessionId) + "</code></p>" + link);
    }

    @GetMapping(value = "/cancel", produces = MediaType.TEXT_HTML_VALUE)
    public String cancel() {
        return page("Payment cancelled", "<p>Checkout was cancelled. Nothing was charged.</p>");
    }

    private String page(String title, String body) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <title>%s</title>
                <style>
                body { font-family: system-ui, sans-serif; margin: 4rem auto; max-width: 40rem; color: #1a1a1a; }
                code { background: #f0f0f0; padding: .15rem .4rem; border-radius: 4px; }
                </style>
                </head>
                <body><h1>%s</h1>%s</body>
                </html>
                """.formatted(title, title, body);
    }

    public record CheckoutRequest(
            @NotBlank String productName,
            @Positive long amount,
            @Positive long quantity) {
    }

    public record CheckoutResponse(String status, String sessionId, String checkoutUrl, String detail) {
    }
}
