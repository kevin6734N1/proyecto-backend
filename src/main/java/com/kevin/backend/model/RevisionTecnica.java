package com.kevin.backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

@Entity
@Table(name = "revisiones_tecnicas")
@Data
public class RevisionTecnica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "calibracion_id", nullable = false)
    private Calibracion calibracion;

    @Column(nullable = false)
    private LocalDate fechaRevision;

    private String revisor; // texto libre, técnico/ingeniero que revisa (aún no hay módulo Usuario)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResultadoRevision resultado = ResultadoRevision.PENDIENTE;

    @Column(columnDefinition = "TEXT")
    private String observaciones;
}
