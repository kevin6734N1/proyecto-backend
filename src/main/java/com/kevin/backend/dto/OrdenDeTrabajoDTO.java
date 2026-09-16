package com.kevin.backend.dto;

import com.kevin.backend.model.EstadoOrdenTrabajo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record OrdenDeTrabajoDTO(
    Long id,
    String numero,
    @NotNull(message = "La cotización es obligatoria")
    Long cotizacionId,
    String cotizacionCodigo,
    Long expedienteId,
    String expedienteNumero,
    String ejecutor,
    String area,
    String lugar,
    @NotNull(message = "La fecha es obligatoria")
    LocalDate fecha,
    LocalTime hora,
    EstadoOrdenTrabajo estado,
    @NotEmpty(message = "La orden de trabajo debe tener al menos una actividad")
    @Valid
    List<DetalleOrdenTrabajoDTO> detalles
) {}
