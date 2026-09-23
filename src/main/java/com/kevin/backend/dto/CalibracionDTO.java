package com.kevin.backend.dto;

import com.kevin.backend.model.EstadoCalibracion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CalibracionDTO(
    Long id,

    @NotNull(message = "La evaluación de aptitud es obligatoria")
    Long evaluacionAptitudId,

    String instrumentoDescripcion, // solo lectura, viene de la evaluación

    String tecnico,
    String procedimiento,

    @NotNull(message = "La fecha es obligatoria")
    LocalDate fecha,

    EstadoCalibracion estado, // solo lectura al crear, nace PROGRAMADA

    @NotEmpty(message = "Debe indicar al menos un patrón usado")
    List<Long> patronesIds,

    List<String> patronesDescripcion, // solo lectura

    @Valid
    List<PuntoCalibracionDTO> puntos // puede venir vacío al crear, se completan al ejecutar
) {}
