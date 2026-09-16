package com.kevin.backend.dto;

import com.kevin.backend.model.EstadoCotizacion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CotizacionDTO(
    Long id,
    String codigo,

    @NotNull(message = "El cliente es obligatorio")
    Long clienteId,

    String clienteRazonSocial,
    Long contactoId,
    String contactoNombre,
    Long expedienteId,
    String expedienteNumero,

    @NotNull(message = "La fecha de emisión es obligatoria")
    LocalDate fechaEmision,

    LocalDate fechaVencimiento,
    EstadoCotizacion estado,
    BigDecimal montoTotal,
    String observaciones,

    @NotEmpty(message = "La cotización debe tener al menos un detalle")
    @Valid
    List<DetalleCotizacionDTO> detalles
) {}
