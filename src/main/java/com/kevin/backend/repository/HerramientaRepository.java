package com.kevin.backend.repository;

import com.kevin.backend.model.Herramienta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HerramientaRepository extends JpaRepository<Herramienta, Long> {
    boolean existsByCodigoInterno(String codigoInterno);
    List<Herramienta> findByActivoTrue();
    List<Herramienta> findByDescripcionContainingIgnoreCase(String texto);
}
