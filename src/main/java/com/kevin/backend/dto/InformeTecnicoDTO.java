package com.kevin.backend.dto;

import com.kevin.backend.model.EstadoInforme;

import java.time.LocalDate;

public record InformeTecnicoDTO(
    Long id,
    String numero, // solo lectura, autogenerado

    Long revisionTecnicaId,
    String instrumentoDescripcion, // solo lectura

    LocalDate fechaEmision,
    EstadoInforme estado,

    Boolean pdfCargado,
    LocalDate fechaCargaPdf,
    LocalDate fechaEnvio,
    LocalDate fechaAnulacion,
    String motivoAnulacion
) {}
