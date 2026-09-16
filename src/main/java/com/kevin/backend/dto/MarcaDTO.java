package com.kevin.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record MarcaDTO(
    Long id,
    @NotBlank(message = "El nombre de la marca es obligatorio")
    String nombre
) {}
