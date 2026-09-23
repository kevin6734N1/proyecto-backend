package com.kevin.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PuntoCalibracionDTO(
    Long id,
    String puntoDescripcion,

    @NotNull(message = "El valor patrón es obligatorio")
    BigDecimal valorPatron,

    @NotNull(message = "El valor medido es obligatorio")
    BigDecimal valorMedido,

    BigDecimal error, // solo lectura, se calcula

    String unidad,
    Boolean dentroDeTolerancia
) {}
