package com.example.authentication.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    /**
     * Admin-only endpoint
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, String>> getAdminDashboard() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Welcome to admin dashboard");
        response.put("access", "Admin access granted");
        return ResponseEntity.ok(response);
    }

    /**
     * Admin stats endpoint
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", 0);
        stats.put("activeUsers", 0);
        stats.put("message", "Admin statistics endpoint");
        return ResponseEntity.ok(stats);
    }
}
