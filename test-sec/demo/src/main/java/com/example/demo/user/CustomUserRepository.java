package com.example.demo.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomUserRepository extends JpaRepository<CustomUser, Long> {
    Optional<CustomUser> findByEmail(String email);

    boolean existsByEmail(String email);

}
