package com.kevin.backend.model;

public enum EstadoInforme {
    GENERADO,       // recién se generó el correlativo
    PDF_CARGADO,    // pendiente de definir cómo se maneja el archivo (ver pregunta a Gesmin)
    APROBADO,       // revisión y aprobación técnica -> disponible para ventas
    ENVIADO         // descargado/enviado por correo al cliente
}
