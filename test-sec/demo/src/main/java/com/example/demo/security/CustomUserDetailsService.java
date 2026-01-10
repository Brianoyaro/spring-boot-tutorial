package com.example.demo.security;

import org.springframework.stereotype.Service;

import com.example.demo.user.CustomUser;
import com.example.demo.user.CustomUserRepository;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.UserDetailsService;


import org.springframework.security.core.userdetails.User;

//Step B
@Service
public class CustomUserDetailsService implements UserDetailsService {
    // User details service implementation would go here
    
    CustomUserRepository userRepository;

    public CustomUserDetailsService(CustomUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Implement the method to load user details from the repository
        CustomUser user = userRepository.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("User not found"));
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }
        return User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .roles(user.getRole().name().replace("ROLE_", ""))
                .build();
    }

}