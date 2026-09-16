package com.kevin.backend.repository;

import com.kevin.backend.model.Modelo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModeloRepository extends JpaRepository<Modelo, Long> {
    boolean existsByNombreIgnoreCase(String nombre);
}
