package com.kevin.backend.dto;

import com.kevin.backend.model.ResultadoRevision;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RevisionTecnicaDTO(
    Long id,

    @NotNull(message = "La calibración es obligatoria")
    Long calibracionId,

    String instrumentoDescripcion, // solo lectura

    @NotNull(message = "La fecha de revisión es obligatoria")
    LocalDate fechaRevision,

    String revisor,

    ResultadoRevision resultado, // solo lectura al crear, nace PENDIENTE

    String observaciones
) {}
