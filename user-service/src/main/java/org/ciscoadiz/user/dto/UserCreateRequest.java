package org.ciscoadiz.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.ciscoadiz.user.entity.UserRole;

import java.time.LocalDate;

public record UserCreateRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String name,
        @NotBlank String surname,
        LocalDate birthdate,
        String status,
        @NotNull UserRole role
) { }
