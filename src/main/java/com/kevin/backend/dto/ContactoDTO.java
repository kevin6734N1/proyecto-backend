package com.kevin.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ContactoDTO(
    Long id,
    @NotBlank(message = "El nombre es obligatorio")
    String nombre,
    @Email(message = "El correo no tiene un formato válido")
    String email,
    String telefono
) {}