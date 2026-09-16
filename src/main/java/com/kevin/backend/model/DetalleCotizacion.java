package com.kevin.backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Table(
    name = "detalle_cotizacion",
    check = @CheckConstraint(
        constraint = "((producto_id IS NOT NULL AND servicio_id IS NULL) OR (producto_id IS NULL AND servicio_id IS NOT NULL))"
    )
)
@Data
public class DetalleCotizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cotizacion_id", nullable = false)
    private Cotizacion cotizacion;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = true)
    private Producto producto;

    @ManyToOne
    @JoinColumn(name = "servicio_id", nullable = true)
    private Servicio servicio;

    @Column(nullable = false)
    private String codigoSnapshot;

    @Column(nullable = false)
    private String nombreSnapshot;

    @Column(nullable = false)
    private BigDecimal precioUnitario;

    private BigDecimal descuentoPorcentaje = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(nullable = false)
    private BigDecimal subtotal;

    private BigDecimal costoAdicional = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String comentarios;

    private String marcaInstrumento;
    private String modeloInstrumento;
    private String serieInstrumento;
}
