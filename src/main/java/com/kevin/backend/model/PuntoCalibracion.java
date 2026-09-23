package com.kevin.backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Table(name = "puntos_calibracion")
@Data
public class PuntoCalibracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "calibracion_id", nullable = false)
    private Calibracion calibracion;

    private String puntoDescripcion; // ej. "10 kg", "50°C", opcional para identificar el punto

    @Column(nullable = false)
    private BigDecimal valorPatron;

    @Column(nullable = false)
    private BigDecimal valorMedido;

    @Column(nullable = false)
    private BigDecimal error; // calculado: valorMedido - valorPatron

    private String unidad; // ej. "kg", "°C", "pH"

    private Boolean dentroDeTolerancia; // lo marca el técnico según el procedimiento aplicable
}
