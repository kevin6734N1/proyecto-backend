package com.kevin.backend.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "herramientas")
@Data
public class Herramienta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String descripcion; // ej. "Patrón de peso 1kg clase F1"

    @Column(nullable = false)
    private String marca;

    @Column(nullable = false)
    private String modelo;

    @Column(nullable = false)
    private String serie;

    @Column(unique = true, nullable = false)
    private String codigoInterno; // código propio de Gesmin para identificar el patrón

    @Column(nullable = false)
    private Boolean activo = true; // soft-delete, igual que Producto/Servicio
}
