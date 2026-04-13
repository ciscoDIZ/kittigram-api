package org.ciscoadiz.auth.dto;

public record AuthRequest(
        String email,
        String password
) {}