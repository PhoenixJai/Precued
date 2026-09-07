package com.precued.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyTokenRequest(@NotBlank String token) {
}
