package com.precued.controller.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ChangeSlideRequest(@NotNull @Min(0) Integer slideIndex) {
}
