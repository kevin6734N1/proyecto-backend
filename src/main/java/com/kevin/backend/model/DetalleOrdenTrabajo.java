package com.kevin.backend.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "detalle_orden_trabajo")
@Data
public class DetalleOrdenTrabajo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "orden_trabajo_id", nullable = false)
    private OrdenDeTrabajo ordenDeTrabajo;

    @Column(nullable = false)
    private String actividad;

    @Column(columnDefinition = "TEXT")
    private String evaluacionInicial;

    @Column(columnDefinition = "TEXT")
    private String conclusiones;

    @Column(columnDefinition = "TEXT")
    private String recomendaciones;

    @Column(nullable = false)
    private Boolean completado = false;
}
