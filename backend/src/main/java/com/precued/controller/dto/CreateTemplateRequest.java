package com.precued.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTemplateRequest(@NotBlank String name) {}
