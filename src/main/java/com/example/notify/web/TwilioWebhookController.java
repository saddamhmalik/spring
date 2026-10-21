package com.example.notify.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class TwilioWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TwilioWebhookController.class);

    private final Map<String, Map<String, String>> callbacks = new ConcurrentHashMap<>();

    @PostMapping("/callbacks/twilio/status")
    public ResponseEntity<Void> statusCallback(@RequestParam Map<String, String> params) {
        String sid = params.getOrDefault("MessageSid", "unknown");
        callbacks.put(sid, params);
        log.info("Twilio status callback for {}: {}", sid, params.get("MessageStatus"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/callbacks/twilio/status")
    public Map<String, Map<String, String>> statusCallbacks() {
        return callbacks;
    }

    @GetMapping("/callbacks/twilio/status/{sid}")
    public ResponseEntity<Map<String, String>> statusCallback(@PathVariable String sid) {
        Map<String, String> callback = callbacks.get(sid);
        return callback == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(callback);
    }

    @PostMapping(value = "/webhooks/twilio/inbound", produces = MediaType.APPLICATION_XML_VALUE)
    public String inboundSms(@RequestParam Map<String, String> params) {
        log.info("Inbound SMS from {}: {}", params.get("From"), params.get("Body"));
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Response><Message>Thanks, we received your message.</Message></Response>
                """;
    }
}
