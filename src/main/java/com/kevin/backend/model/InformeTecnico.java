package com.kevin.backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

@Entity
@Table(name = "informes_tecnicos")
@Data
public class InformeTecnico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String numero; // ej. IT2609nnn

    @ManyToOne
    @JoinColumn(name = "revision_tecnica_id", nullable = false)
    private RevisionTecnica revisionTecnica;

    @Column(nullable = false)
    private LocalDate fechaEmision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoInforme estado = EstadoInforme.GENERADO;

    // --- PLACEHOLDER: pendiente definir con Gesmin cómo se maneja el PDF firmado ---
    @Column(nullable = false)
    private Boolean pdfCargado = false;
    private LocalDate fechaCargaPdf;

    private LocalDate fechaEnvio; // se llena al "descargar/enviar por correo"

    private LocalDate fechaAnulacion;

    @Column(columnDefinition = "TEXT")
    private String motivoAnulacion;
}
