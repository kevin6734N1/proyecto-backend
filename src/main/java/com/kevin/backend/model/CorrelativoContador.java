package com.kevin.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "correlativos_contadores")
@Data
public class CorrelativoContador {
    @Id
    @Column(length = 12)
    private String prefijo;

    @Column(nullable = false)
    private long ultimo;
}
