package com.example.demo.security;

import com.example.demo.user.CustomUserRepository;
import com.example.demo.user.CustomUser;
import com.example.demo.user.CustomUserRole;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;


// Step B
@Component
public class DataLoader implements CommandLineRunner {

    private final CustomUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataLoader(CustomUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        // Preload some users into the database
        if (!userRepository.existsByEmail("test@email.com")) {
            CustomUser user = new CustomUser();
            user.setEmail("test@email.com");
            user.setPassword(passwordEncoder.encode("password"));
            user.setRole(CustomUserRole.ROLE_USER);
            user.setEnabled(true);
            userRepository.save(user);
            System.out.println("Dummy user created: " + user.getEmail() + " with password: password");
        }
        if (!userRepository.existsByEmail("admin@email.com")) {
            CustomUser adminUser = new CustomUser();
            adminUser.setEmail("admin@email.com");
            adminUser.setPassword(passwordEncoder.encode("password"));
            adminUser.setRole(CustomUserRole.ROLE_ADMIN);
            adminUser.setEnabled(true);
            userRepository.save(adminUser);
            System.out.println("admin user created: " + adminUser.getEmail() + " with password: password");
        }
    }
}
