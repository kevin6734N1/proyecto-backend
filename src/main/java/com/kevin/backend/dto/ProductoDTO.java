package com.kevin.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ProductoDTO(
    Long id,

    @NotBlank(message = "El código es obligatorio")
    String codigo,

    @NotBlank(message = "El nombre del producto es obligatorio")
    String nombre,

    @NotNull(message = "La marca es obligatoria")
    Long marcaId,

    String marcaNombre,

    @NotNull(message = "El modelo es obligatorio")
    Long modeloId,

    String modeloNombre,

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.0", inclusive = true, message = "El precio no puede ser negativo")
    BigDecimal precio,

    @NotNull(message = "El stock es obligatorio")
    @Min(value = 0, message = "El stock no puede ser negativo")
    Integer stock
) {}
