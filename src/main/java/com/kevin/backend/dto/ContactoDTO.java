package com.kevin.backend.dto;

public record ContactoDTO(
    Long id,
    String nombre,
    String email,
    String telefono
) {}