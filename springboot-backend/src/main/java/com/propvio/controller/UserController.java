package com.propvio.controller;

import com.propvio.dto.request.LoginRequest;
import com.propvio.dto.request.RegisterRequest;
import com.propvio.dto.response.ApiResponse;
import com.propvio.model.Admin;
import com.propvio.model.User;
import com.propvio.repository.AdminRepository;
import com.propvio.security.JwtUtil;
import com.propvio.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final AdminRepository adminRepo;
    private final PasswordEncoder passwordEncoder;

    // POST /api/user/register
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<?>> register(@Valid @RequestBody RegisterRequest req) {
        if (userService.emailExists(req.getEmail())) {
            return ResponseEntity.ok(ApiResponse.fail("An account with this email already exists."));
        }
        User newUser = userService.register(req.getName(), req.getEmail(), req.getPassword());
        String token = jwtUtil.generateToken(newUser.getId(), false);
        return ResponseEntity.ok(ApiResponse.ok(
            "Registration successful!",
            Map.of("token", token, "user", Map.of("name", newUser.getName(), "email", newUser.getEmail()))
        ));
    }

    // POST /api/user/login
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@Valid @RequestBody LoginRequest req) {
        Optional<User> opt = userService.findByEmail(req.getEmail());
        if (opt.isEmpty()) return ResponseEntity.ok(ApiResponse.fail("Email not found"));

        User user = opt.get();

        // Legacy user auto-verify
        if (user.getIsEmailVerified() == null && user.getEmailVerificationToken() == null) {
            user.setIsEmailVerified(true);
        }
        if (Boolean.FALSE.equals(user.getIsEmailVerified())) {
            return ResponseEntity.ok(ApiResponse.fail("Please verify your email before logging in."));
        }
        if (!userService.checkPassword(user, req.getPassword())) {
            return ResponseEntity.ok(ApiResponse.fail("Invalid password"));
        }

        String token = jwtUtil.generateToken(user.getId(), req.isRememberMe());
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "token",     token,
            "user",      Map.of("name", user.getName(), "email", user.getEmail()),
            "expiresIn", req.isRememberMe() ? "30 days" : "7 days"
        )));
    }

    // GET /api/user/verify/:token
    @GetMapping("/verify/{token}")
    public ResponseEntity<ApiResponse<?>> verifyEmail(@PathVariable String token) {
        Optional<User> opt = userService.verifyEmail(token);
        if (opt.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Invalid or expired verification link."));
        }
        User user = opt.get();
        String authToken = jwtUtil.generateToken(user.getId(), true);
        return ResponseEntity.ok(ApiResponse.ok(
            "Email verified successfully! You can now log in.",
            Map.of("token", authToken,
                   "user",  Map.of("name", user.getName(), "email", user.getEmail()))
        ));
    }

    // POST /api/user/forgot
    @PostMapping("/forgot")
    public ResponseEntity<ApiResponse<?>> forgotPassword(@RequestBody Map<String, String> body) {
        try {
            userService.initiatePasswordReset(body.get("email"));
            return ResponseEntity.ok(ApiResponse.ok("Email sent", null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(ApiResponse.fail("Email not found"));
        }
    }

    // POST /api/user/reset/:token
    @PostMapping("/reset/{token}")
    public ResponseEntity<ApiResponse<?>> resetPassword(@PathVariable String token,
                                                         @RequestBody Map<String, String> body) {
        try {
            userService.resetPassword(token, body.get("password"));
            return ResponseEntity.ok(ApiResponse.ok("Password reset successful", null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Invalid or expired token"));
        }
    }

    // POST /api/user/admin  — admin login
    @PostMapping("/admin")
    public ResponseEntity<ApiResponse<?>> adminLogin(@RequestBody Map<String, String> body) {
        Optional<Admin> opt = adminRepo.findByEmail(body.get("email"));
        if (opt.isEmpty()) return ResponseEntity.badRequest().body(ApiResponse.fail("Invalid credentials"));

        Admin admin = opt.get();
        if (!passwordEncoder.matches(body.get("password"), admin.getPassword())) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Invalid credentials"));
        }
        admin.setLastLogin(LocalDateTime.now());
        adminRepo.save(admin);

        // Admin uses a sentinel Long to distinguish from regular users
        String token = jwtUtil.generateToken(-admin.getId(), false);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("token", token)));
    }

    // GET /api/user/me
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<?>> getMe(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(
            Map.of("id", user.getId(), "name", user.getName(), "email", user.getEmail())
        ));
    }

    // POST /api/user/logout
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<?>> logout() {
        return ResponseEntity.ok(ApiResponse.ok("Logged out", null));
    }
}
