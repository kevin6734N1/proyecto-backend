package com.kevin.backend.dto;

import com.kevin.backend.model.ResultadoEvaluacion;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record EvaluacionAptitudDTO(
    Long id,
    @NotNull(message = "La orden de trabajo es obligatoria")
    Long ordenDeTrabajoId,
    String ordenDeTrabajoNumero, // solo lectura

    @NotNull(message = "El instrumento es obligatorio")
    Long instrumentoId,
    String instrumentoDescripcion, // solo lectura, ej. "Fluke 87V - Serie ABC123"

    LocalDate fechaEvaluacion,
    ResultadoEvaluacion resultado, // solo lectura al crear, nace PENDIENTE
    String observaciones,
    String comunicacionCliente,   // solo lectura, se llena vía endpoint de respuesta-cliente
    Boolean autorizaContinuar     // solo lectura, idem
) {}
