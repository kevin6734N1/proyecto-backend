package com.kevin.backend.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "contactos")
@Data
public class Contacto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;

    private String email;

    private String telefono;

    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;
}