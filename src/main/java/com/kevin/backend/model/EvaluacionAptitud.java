package com.kevin.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Table(name = "evaluaciones_aptitud")
@Data
public class EvaluacionAptitud {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "orden_trabajo_id", nullable = false)
    private OrdenDeTrabajo ordenDeTrabajo;

    @ManyToOne
    @JoinColumn(name = "instrumento_id", nullable = false)
    private Instrumento instrumento;

    @Column(nullable = false)
    private LocalDate fechaEvaluacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResultadoEvaluacion resultado = ResultadoEvaluacion.PENDIENTE;

    @Column(columnDefinition = "TEXT")
    private String observaciones; // por qué no es apto, detalle técnico

    // --- se llenan solo si resultado = NO_APTO y hay comunicación con el cliente ---
    @Column(columnDefinition = "TEXT")
    private String comunicacionCliente;

    private Boolean autorizaContinuar; // null = aún sin respuesta del cliente
}
