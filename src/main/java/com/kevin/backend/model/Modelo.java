package com.kevin.backend.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "modelos")
@Data
public class Modelo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String nombre;
}
