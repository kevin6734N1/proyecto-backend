package com.kevin.backend.dto;

import java.util.List;

public record ClienteDTO(
    Long id,
    String razonSocial,
    String ruc,
    String direccion,
    String rubro,
    List<ContactoDTO> contactos
) {}