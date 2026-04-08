package com.riskguard.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    // Gerçek bir canlı (production) ortamında bu anahtar application.yml içinde tutulur. 
    // Şimdilik sistem her açıldığında rastgele ve kırılamaz bir anahtar üretiyoruz.
    private final Key SECRET_KEY = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    private final long EXPIRATION_TIME = 1000 * 60 * 60 * 24; // Token'ın geçerlilik süresi: 24 Saat

    // Giriş yapan kullanıcıya özel bilet (Token) üretir
    public String generateToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(SECRET_KEY)
                .compact();
    }

    // Gelen biletin içinden kullanıcının e-postasını okur
    public String extractEmail(String token) {
        return getClaims(token).getSubject();
    }

    // Biletin sahte olup olmadığını ve süresinin geçip geçmediğini kontrol eder
    public boolean validateToken(String token) {
        try {
            return !getClaims(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(SECRET_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}