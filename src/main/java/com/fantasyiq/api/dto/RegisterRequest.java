package com.fantasyiq.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        // 72 is BCrypt's input limit, not an arbitrary choice
        @NotBlank @Size(min = 8, max = 72) String password,
        String displayName
) {
}
