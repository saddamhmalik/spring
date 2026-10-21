package com.example.notify.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/email")
public class EmailController {

    private final JavaMailSender mailSender;
    private final String smtpHost;
    private final String from;

    public EmailController(JavaMailSender mailSender,
                           @Value("${spring.mail.host:}") String smtpHost,
                           @Value("${app.email.from:no-reply@example.com}") String from) {
        this.mailSender = mailSender;
        this.smtpHost = smtpHost;
        this.from = from;
    }

    @PostMapping("/send")
    public ResponseEntity<EmailResponse> send(@Valid @RequestBody EmailRequest request) {
        if (smtpHost.isBlank()) {
            return ResponseEntity.ok(new EmailResponse("skipped",
                    "Set SMTP_HOST, SMTP_PORT, SMTP_USERNAME and SMTP_PASSWORD to send real email."));
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(request.to());
        message.setSubject(request.subject());
        message.setText(request.body());
        mailSender.send(message);

        return ResponseEntity.ok(new EmailResponse("sent", "Email sent to " + request.to()));
    }

    public record EmailRequest(
            @NotBlank @Email String to,
            @NotBlank String subject,
            @NotBlank String body) {
    }

    public record EmailResponse(String status, String detail) {
    }
}
