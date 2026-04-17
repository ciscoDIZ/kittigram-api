package org.ciscoadiz.auth.service;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Set;

@ApplicationScoped
public class JwtTokenService {

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    public String generateAccessToken(long userId, String email, String role) {
        return Jwt.issuer(issuer)
                .subject(String.valueOf(userId))
                .claim("email", email)
                .groups(Set.of(role))
                .expiresIn(900)
                .sign();
    }
}