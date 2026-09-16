package com.kevin.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record DetalleOrdenTrabajoDTO(
    Long id,
    @NotBlank(message = "La actividad es obligatoria")
    String actividad,
    String evaluacionInicial,
    String conclusiones,
    String recomendaciones,
    Boolean completado
) {}
