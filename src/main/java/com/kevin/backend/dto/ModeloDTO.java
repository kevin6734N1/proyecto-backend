package com.kevin.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record ModeloDTO(
    Long id,
    @NotBlank(message = "El nombre del modelo es obligatorio")
    String nombre
) {}
