package com.example.authentication.service;

import com.example.authentication.dto.AuthenticationRequest;
import com.example.authentication.dto.AuthenticationResponse;
import com.example.authentication.dto.RefreshTokenRequest;
import com.example.authentication.dto.RegisterRequest;
import com.example.authentication.entity.Role;
import com.example.authentication.entity.User;
import com.example.authentication.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager,
            UserDetailsService userDetailsService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Register a new user
     */
    @Transactional
    public AuthenticationResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        // Check if user already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalStateException("Email already registered");
        }

        // Create new user
        User user = new User(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getFirstName(),
                request.getLastName(),
                request.getRole() != null ? request.getRole() : Role.USER
        );

        // Set login metadata
        user.setLastLogin(LocalDateTime.now());
        user.setLastLoginIp(getClientIp(httpRequest));

        userRepository.save(user);

        // Generate tokens
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return new AuthenticationResponse(
                accessToken,
                refreshToken,
                jwtService.getAccessTokenExpiration()
        );
    }

    /**
     * Authenticate user and generate tokens
     */
    @Transactional
    public AuthenticationResponse authenticate(AuthenticationRequest request, HttpServletRequest httpRequest) {
        // Authenticate user
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // Load user details
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Update login metadata
        user.setLastLogin(LocalDateTime.now());
        user.setLastLoginIp(getClientIp(httpRequest));
        userRepository.save(user);

        // Generate tokens
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return new AuthenticationResponse(
                accessToken,
                refreshToken,
                jwtService.getAccessTokenExpiration()
        );
    }

    /**
     * Refresh access token using refresh token
     */
    public AuthenticationResponse refreshToken(RefreshTokenRequest request, HttpServletRequest httpRequest) {
        final String refreshToken = request.getRefreshToken();
        final String userEmail;

        try {
            // Extract username from refresh token
            userEmail = jwtService.extractUsername(refreshToken);

            if (userEmail != null) {
                // Load user details
                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

                // Validate refresh token
                if (jwtService.isTokenValid(refreshToken, userDetails)) {
                    // Validate issuer and audience
                    if (jwtService.validateIssuer(refreshToken) && jwtService.validateAudience(refreshToken)) {
                        
                        // Perform IP/device check for anomaly detection (best practice)
                        User user = userRepository.findByEmail(userEmail)
                                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
                        
                        String currentIp = getClientIp(httpRequest);
                        String lastLoginIp = user.getLastLoginIp();
                        
                        // Optional: Implement more sophisticated device/IP validation here
                        // For now, we'll log the IP change but still allow token refresh
                        if (lastLoginIp != null && !lastLoginIp.equals(currentIp)) {
                            // Log potential security event
                            System.out.println("Warning: IP address changed during token refresh for user: " + userEmail);
                        }

                        // Generate new access token
                        String accessToken = jwtService.generateAccessToken(userDetails);

                        return new AuthenticationResponse(
                                accessToken,
                                refreshToken,
                                jwtService.getAccessTokenExpiration()
                        );
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Invalid refresh token");
        }

        throw new IllegalStateException("Invalid refresh token");
    }

    /**
     * Extract client IP address from request
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
