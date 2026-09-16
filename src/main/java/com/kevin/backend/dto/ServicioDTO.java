package com.kevin.backend.dto;

import com.kevin.backend.model.TipoServicio;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ServicioDTO(
    Long id,

    @NotBlank(message = "El código es obligatorio")
    String codigo,

    @NotBlank(message = "El nombre del servicio es obligatorio")
    String nombre,

    @NotNull(message = "El tipo de servicio es obligatorio")
    TipoServicio tipoServicio,

    @NotBlank(message = "El tipo de equipo aplicable es obligatorio")
    String tipoEquipoAplicable,

    String descripcion,

    @NotNull(message = "El precio de venta es obligatorio")
    @DecimalMin(value = "0.0", inclusive = true, message = "El precio de venta no puede ser negativo")
    BigDecimal precioVenta
) {}
