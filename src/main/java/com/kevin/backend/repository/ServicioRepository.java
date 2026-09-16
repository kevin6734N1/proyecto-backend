package com.kevin.backend.repository;

import com.kevin.backend.model.Servicio;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ServicioRepository extends JpaRepository<Servicio, Long> {
    List<Servicio> findByNombreContainingIgnoreCase(String texto);
    boolean existsByCodigo(String codigo);
}
