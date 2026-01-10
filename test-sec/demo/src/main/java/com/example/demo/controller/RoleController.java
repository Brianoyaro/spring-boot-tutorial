package com.example.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoleController {

    @GetMapping("/user")
    public String userAccess() {
        return "User Content.";
    }

    @GetMapping("/admin")
    public String adminAccess() {
        return "Admin Content.";
    }
}
