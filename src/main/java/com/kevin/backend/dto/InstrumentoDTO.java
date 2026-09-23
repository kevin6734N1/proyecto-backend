package com.kevin.backend.dto;

import com.kevin.backend.model.UbicacionInstrumento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InstrumentoDTO(
    Long id,
    @NotBlank(message = "El tipo de instrumento es obligatorio")
    String tipo,
    @NotBlank(message = "La marca es obligatoria")
    String marca,
    @NotBlank(message = "El modelo es obligatorio")
    String modelo,
    @NotBlank(message = "La serie es obligatoria")
    String serie,
    String codigoCliente,
    @NotNull(message = "El cliente es obligatorio")
    Long clienteId,
    String clienteRazonSocial, // solo lectura
    UbicacionInstrumento ubicacion // solo lectura al crear, nace UBICADO_EN_CLIENTE
) {}
