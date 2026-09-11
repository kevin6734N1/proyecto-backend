package com.kevin.backend.dto;

import com.kevin.backend.model.EstadoExpediente;
import java.time.LocalDate;

public record ExpedienteDTO(
    Long id,
    String numero,
    LocalDate fecha,
    Long clienteId,
    String clienteRazonSocial,
    EstadoExpediente estado
) {}