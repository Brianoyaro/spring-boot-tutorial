package com.example.demo.security.auth;

import com.example.demo.security.auth.dto.AuthResponse;
import com.example.demo.security.auth.dto.LoginRequest;
import com.example.demo.security.auth.dto.RegisterRequest;
import com.example.demo.security.jwt.JwtService;
import com.example.demo.user.CustomUser;
import com.example.demo.user.CustomUserRepository;
import com.example.demo.user.CustomUserRole;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;


@Service
public class AuthService {
    private final AuthenticationManager authenticationManager; //to create an authentication object for login
    private final PasswordEncoder passwordEncoder; //encode/decode passwords
    private final JwtService jwtService; // generate a token
    private final CustomUserRepository userRepository; // access the database

    public AuthService(AuthenticationManager authenticationManager,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       CustomUserRepository userRepository) {
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void register(RegisterRequest request) {
        // Check if a similar email exists in the database
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // if no email exists, create a new user object and store them in the database
        CustomUser user = new CustomUser();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword())); // encode the password
        user.setRole(CustomUserRole.ROLE_USER); //set the default role of USER
        user.setEnabled(true);

        userRepository.save(user);
    }


    public AuthResponse login(LoginRequest request) {
        // authenticate the user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // generate a jwt token
        String token = jwtService.generateToken(
//                (userDetails) authentication.getPrincipal();
                (UserDetails) Objects.requireNonNull(authentication.getPrincipal())
        );
        return new AuthResponse(token);
    }
}
