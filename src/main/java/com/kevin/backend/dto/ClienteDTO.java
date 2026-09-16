package com.kevin.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record ClienteDTO(
    Long id,
    @NotBlank(message = "La razón social es obligatoria")
    String razonSocial,
    @NotBlank(message = "El RUC es obligatorio")
    @Pattern(regexp = "\\d{11}", message = "El RUC debe tener 11 dígitos")
    String ruc,
    String direccion,
    String rubro,
    List<ContactoDTO> contactos
) {}