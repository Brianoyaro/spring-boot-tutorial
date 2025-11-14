package com.briandomain.brian_e_commerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
//@ComponentScan(basePackages = "com.briandomain.brian_e_commerce")
//@EnableAutoConfiguration
public class BrianECommerceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BrianECommerceApplication.class, args);
	}

}
