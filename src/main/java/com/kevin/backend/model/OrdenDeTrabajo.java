package com.kevin.backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ordenes_trabajo")
@Data
public class OrdenDeTrabajo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String numero; // ej. OT2600001

    @ManyToOne
    @JoinColumn(name = "cotizacion_id", nullable = false)
    private Cotizacion cotizacion;

    @ManyToOne
    @JoinColumn(name = "expediente_id", nullable = true)
    private Expediente expediente;

    private String ejecutor;
    private String area;
    private String lugar;

    @Column(nullable = false)
    private LocalDate fecha;

    private LocalTime hora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoOrdenTrabajo estado = EstadoOrdenTrabajo.PENDIENTE;

    @OneToMany(mappedBy = "ordenDeTrabajo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleOrdenTrabajo> detalles = new ArrayList<>();
}
