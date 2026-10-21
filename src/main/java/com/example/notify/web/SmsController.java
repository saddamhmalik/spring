package com.example.notify.web;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.Account;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sms")
public class SmsController {

    private final String accountSid;
    private final String apiKeySid;
    private final String apiKeySecret;
    private final String authToken;
    private final String fromNumber;
    private final String publicUrl;

    public SmsController(@Value("${app.twilio.account-sid:}") String accountSid,
                         @Value("${app.twilio.api-key-sid:}") String apiKeySid,
                         @Value("${app.twilio.api-key-secret:}") String apiKeySecret,
                         @Value("${app.twilio.auth-token:}") String authToken,
                         @Value("${app.twilio.from-number:}") String fromNumber,
                         @Value("${app.twilio.public-url:}") String publicUrl) {
        this.accountSid = accountSid;
        this.apiKeySid = apiKeySid;
        this.apiKeySecret = apiKeySecret;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
        this.publicUrl = publicUrl;
    }

    @GetMapping("/account")
    public ResponseEntity<SmsResponse> account() {
        if (!credentialsPresent()) {
            return ResponseEntity.ok(new SmsResponse("skipped", null,
                    "Set TWILIO_ACCOUNT_SID plus TWILIO_API_KEY_SID and TWILIO_API_KEY_SECRET (or TWILIO_AUTH_TOKEN)."));
        }

        authenticate();
        Account account = Account.fetcher(accountSid).fetch();

        return ResponseEntity.ok(new SmsResponse("ok", account.getSid(),
                account.getFriendlyName() + " / " + account.getType() + " / " + account.getStatus()));
    }

    @PostMapping("/send")
    public ResponseEntity<SmsResponse> send(@Valid @RequestBody SmsRequest request) {
        if (!credentialsPresent()) {
            return ResponseEntity.ok(new SmsResponse("skipped", null,
                    "Set TWILIO_ACCOUNT_SID plus TWILIO_API_KEY_SID and TWILIO_API_KEY_SECRET (or TWILIO_AUTH_TOKEN)."));
        }

        if (fromNumber.isBlank()) {
            return ResponseEntity.ok(new SmsResponse("skipped", null,
                    "Set TWILIO_FROM_NUMBER to a number owned by the account. Trial accounts need a purchased number."));
        }

        authenticate();

        Message message = Message.creator(
                        new PhoneNumber(request.to()),
                        new PhoneNumber(fromNumber),
                        request.message())
                .setStatusCallback(statusCallbackUrl())
                .create();

        return ResponseEntity.ok(new SmsResponse("sent", message.getSid(), message.getStatus().toString()));
    }

    private void authenticate() {
        if (!apiKeySid.isBlank() && !apiKeySecret.isBlank()) {
            Twilio.init(apiKeySid, apiKeySecret, accountSid);
        } else {
            Twilio.init(accountSid, authToken, accountSid);
        }
    }

    private boolean credentialsPresent() {
        boolean hasKey = !apiKeySid.isBlank() && !apiKeySecret.isBlank();
        boolean hasToken = !authToken.isBlank();
        return !accountSid.isBlank() && (hasKey || hasToken);
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
