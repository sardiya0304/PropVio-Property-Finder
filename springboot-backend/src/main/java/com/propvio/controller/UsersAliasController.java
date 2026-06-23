package com.propvio.controller;

import com.propvio.model.Admin;
import com.propvio.repository.AdminRepository;
import com.propvio.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Alias controller so the admin frontend's POST /api/users/admin works
 * alongside the original POST /api/user/admin endpoint.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UsersAliasController {

    private final AdminRepository adminRepo;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    // POST /api/users/admin  — admin login (plural alias used by admin frontend)
    // Returns flat { success, token } to match Node.js backend format that login.jsx expects
    @PostMapping("/admin")
    public ResponseEntity<Map<String, Object>> adminLogin(@RequestBody Map<String, String> body) {
        Map<String, Object> res = new HashMap<>();
        Optional<Admin> opt = adminRepo.findByEmail(body.get("email"));
        if (opt.isEmpty()) {
            res.put("success", false);
            res.put("message", "Invalid credentials");
            return ResponseEntity.badRequest().body(res);
        }

        Admin admin = opt.get();
        if (!passwordEncoder.matches(body.get("password"), admin.getPassword())) {
            res.put("success", false);
            res.put("message", "Invalid credentials");
            return ResponseEntity.badRequest().body(res);
        }
        admin.setLastLogin(LocalDateTime.now());
        adminRepo.save(admin);

        String token = jwtUtil.generateToken(-admin.getId(), false);
        res.put("success", true);
        res.put("token", token);
        return ResponseEntity.ok(res);
    }
}
