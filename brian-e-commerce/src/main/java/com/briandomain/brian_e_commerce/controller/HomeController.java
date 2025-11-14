package com.briandomain.brian_e_commerce.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/home")
    public String welcome() {
        return "Hi, welcome to my fancy e-commerce";
    }
}
