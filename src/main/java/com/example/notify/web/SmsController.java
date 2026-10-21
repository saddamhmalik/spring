package com.example.notify.web;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sms")
public class SmsController {

    private final String accountSid;
    private final String authToken;
    private final String fromNumber;
    private final String publicUrl;

    public SmsController(@Value("${app.twilio.account-sid:}") String accountSid,
                         @Value("${app.twilio.auth-token:}") String authToken,
                         @Value("${app.twilio.from-number:}") String fromNumber,
                         @Value("${app.twilio.public-url:}") String publicUrl) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
        this.publicUrl = publicUrl;
    }

    @PostMapping("/send")
    public ResponseEntity<SmsResponse> send(@Valid @RequestBody SmsRequest request) {
        if (accountSid.isBlank() || authToken.isBlank() || fromNumber.isBlank()) {
            return ResponseEntity.ok(new SmsResponse("skipped", null,
                    "Set TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN and TWILIO_FROM_NUMBER to send real SMS."));
        }

        Twilio.init(accountSid, authToken);

        Message message = Message.creator(
                        new PhoneNumber(request.to()),
                        new PhoneNumber(fromNumber),
                        request.message())
                .setStatusCallback(statusCallbackUrl())
                .create();

        return ResponseEntity.ok(new SmsResponse("sent", message.getSid(), message.getStatus().toString()));
    }

    private String statusCallbackUrl() {
        if (publicUrl.isBlank()) {
            return null;
        }
        return publicUrl.replaceAll("/+$", "") + "/api/callbacks/twilio/status";
    }

    public record SmsRequest(@NotBlank String to, @NotBlank String message) {
    }

    public record SmsResponse(String status, String sid, String detail) {
    }
}
