package com.example.demo.security.jwt;


import io.jsonwebtoken.Claims;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
//import io.jsonwebtoken.*;
//import io.jsonwebtoken.security.Keys;
//import java.security.Key;


import javax.crypto.SecretKey;
import io.jsonwebtoken.Jwts;

import java.util.Date;

@Service
public class JwtService {
//    public static final String SECRET_KEY = "secretkey1234567890"; // Example secret key
//    private final Key key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes());

    private final SecretKey key = Jwts.SIG.HS256.key().build();

    public String generateToken(UserDetails userDetails) {
        long jwtExpirationMs = 86400000; // 1 day in milliseconds

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
//                .signWith(key, SignatureAlgorithm.HS256)
                .signWith(key) //one in the docs for 0.13.0 version of io.jjwt
                .compact();
    }

    // Step D
    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    private Claims extractClaims(String token) {
        //
//        Jwts.parserBuilder()
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        //
        return extractUsername(token).equals(userDetails.getUsername()) && !isTokenExpired(token);
    }


    private boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }
}
