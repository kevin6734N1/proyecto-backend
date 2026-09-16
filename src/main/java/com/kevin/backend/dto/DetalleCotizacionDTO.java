package com.kevin.backend.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record DetalleCotizacionDTO(
    Long id,
    Long productoId,
    Long servicioId,
    String codigoSnapshot,
    String nombreSnapshot,
    BigDecimal precioUnitario,

    @DecimalMin(value = "0.0", message = "El descuento no puede ser negativo")
    @DecimalMax(value = "100.0", message = "El descuento no puede superar 100%")
    BigDecimal descuentoPorcentaje,

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    Integer cantidad,

    BigDecimal subtotal,
    BigDecimal costoAdicional,
    String comentarios,
    String marcaInstrumento,
    String modeloInstrumento,
    String serieInstrumento
) {}
