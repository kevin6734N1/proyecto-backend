package com.kevin.backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "calibraciones")
@Data
public class Calibracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "evaluacion_aptitud_id", nullable = false)
    private EvaluacionAptitud evaluacionAptitud;

    private String tecnico; // texto libre, igual que ejecutor en OrdenDeTrabajo (aún no hay módulo Usuario)

    private String procedimiento; // ej. "PR-CAL-005 Calibración de balanzas"

    @Column(nullable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoCalibracion estado = EstadoCalibracion.PROGRAMADA;

    @ManyToMany
    @JoinTable(
        name = "calibracion_patrones",
        joinColumns = @JoinColumn(name = "calibracion_id"),
        inverseJoinColumns = @JoinColumn(name = "herramienta_id")
    )
    private List<Herramienta> patrones = new ArrayList<>();

    @OneToMany(mappedBy = "calibracion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PuntoCalibracion> puntos = new ArrayList<>();
}
