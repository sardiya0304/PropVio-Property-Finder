package com.propvio.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.website.url}")
    private String websiteUrl;

    @Async
    public void sendEmailVerification(String to, String name, String token) {
        String url = websiteUrl + "/verify-email/" + token;
        String html = """
                <div style="font-family:sans-serif;max-width:600px;margin:0 auto">
                  <h2 style="color:#1a56db">Verify your Propvio account</h2>
                  <p>Hi %s,</p>
                  <p>Click the button below to verify your email address.</p>
                  <a href="%s" style="display:inline-block;padding:12px 24px;background:#1a56db;color:#fff;text-decoration:none;border-radius:6px">
                    Verify Email
                  </a>
                  <p style="color:#6b7280;font-size:13px;margin-top:16px">Link expires in 24 hours.</p>
                </div>
                """.formatted(name, url);
        send(to, "Verify your Propvio email", html);
    }

    @Async
    public void sendWelcomeEmail(String to, String name) {
        String html = """
                <div style="font-family:sans-serif;max-width:600px;margin:0 auto">
                  <h2 style="color:#1a56db">Welcome to Propvio!</h2>
                  <p>Hi %s, your account is now verified.</p>
                  <p>Start exploring properties at <a href="%s">Propvio</a>.</p>
                </div>
                """.formatted(name, websiteUrl);
        send(to, "Welcome to Propvio!", html);
    }

    @Async
    public void sendPasswordResetEmail(String to, String token) {
        String url = websiteUrl + "/reset/" + token;
        String html = """
                <div style="font-family:sans-serif;max-width:600px;margin:0 auto">
                  <h2 style="color:#1a56db">Reset your password</h2>
                  <p>Click the button below to reset your Propvio password.</p>
                  <a href="%s" style="display:inline-block;padding:12px 24px;background:#1a56db;color:#fff;text-decoration:none;border-radius:6px">
                    Reset Password
                  </a>
                  <p style="color:#6b7280;font-size:13px;margin-top:16px">Link expires in 10 minutes.</p>
                </div>
                """.formatted(url);
        send(to, "Reset your Propvio password", html);
    }

    private void send(String to, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);
        } catch (Exception e) {
            // Log but don't crash — same behaviour as Node.js try/catch around email
            System.err.println("[EmailService] Failed to send to " + to + ": " + e.getMessage());
        }
    }
}
