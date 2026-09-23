package com.kevin.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record HerramientaDTO(
    Long id,

    @NotBlank(message = "La descripción es obligatoria")
    String descripcion,

    @NotBlank(message = "La marca es obligatoria")
    String marca,

    @NotBlank(message = "El modelo es obligatorio")
    String modelo,

    @NotBlank(message = "La serie es obligatoria")
    String serie,

    @NotBlank(message = "El código interno es obligatorio")
    String codigoInterno,

    Boolean activo // solo lectura, nace true
) {}
