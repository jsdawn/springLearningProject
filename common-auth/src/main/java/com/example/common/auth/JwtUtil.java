package com.example.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

public class JwtUtil {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtUtil(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = buildSecretKey(jwtProperties.getSecretBase64());
    }

    public String generateAccessToken(String userId, String username, String jti) {
        long expireSeconds = jwtProperties.getAccessTokenExpireSeconds() != null
                ? jwtProperties.getAccessTokenExpireSeconds()
                : 3600L;

        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        Date exp = new Date(nowMillis + expireSeconds * 1000);

        return Jwts.builder()
                .setIssuer(jwtProperties.getIssuer())
                .setSubject(userId)
                .setId(jti)
                .setIssuedAt(now)
                .setExpiration(exp)
                .claim("username", username)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseAndValidate(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isEmpty()) {
            return null;
        }
        String prefix = jwtProperties.getHeaderPrefix() != null ? jwtProperties.getHeaderPrefix() : AuthConstants.BEARER_PREFIX;
        if (!authorizationHeader.startsWith(prefix)) {
            return null;
        }
        return authorizationHeader.substring(prefix.length()).trim();
    }

    private SecretKey buildSecretKey(String secretBase64) {
        if (secretBase64 == null || secretBase64.trim().isEmpty()) {
            throw new IllegalArgumentException("jwt.secretBase64 is required");
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secretBase64);
        } catch (IllegalArgumentException e) {
            keyBytes = secretBase64.getBytes(StandardCharsets.UTF_8);
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }
}

