package com.propvio.controller;

import com.propvio.dto.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/forms")
public class ContactController {

    // POST /api/forms/submit
    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<?>> submit(@RequestBody Map<String, String> body) {
        // Log the contact form submission; email integration can be added later
        String name    = body.getOrDefault("name", "");
        String email   = body.getOrDefault("email", "");
        String message = body.getOrDefault("message", "");
        System.out.printf("[Contact] %s <%s>: %s%n", name, email, message);
        return ResponseEntity.ok(ApiResponse.ok("Message received! We'll get back to you soon.", null));
    }
}
