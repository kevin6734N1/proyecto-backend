package com.kevin.backend.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "instrumentos")
@Data
public class Instrumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tipo; // ej. "Agitador", "pH-metro"

    @Column(nullable = false)
    private String marca; // texto libre: fabricante externo del equipo del cliente

    @Column(nullable = false)
    private String modelo;

    @Column(nullable = false)
    private String serie;

    private String codigoCliente; // código interno que usa el cliente para identificarlo

    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UbicacionInstrumento ubicacion = UbicacionInstrumento.UBICADO_EN_CLIENTE;
}
