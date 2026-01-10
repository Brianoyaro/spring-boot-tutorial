package com.example.demo.controller;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/public")
    public String publicArea() {
        return "Welcome to the Public Area!";
    }

    @GetMapping("/private")
    public String privateArea(Authentication authentication) {
        return "Welcome to the Private Area! You are logged in as: " + authentication.getName();
    }
}