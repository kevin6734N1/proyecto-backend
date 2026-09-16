package com.kevin.backend.repository;

import com.kevin.backend.model.DetalleCotizacion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetalleCotizacionRepository extends JpaRepository<DetalleCotizacion, Long> {
    boolean existsByProductoId(Long productoId);
    boolean existsByServicioId(Long servicioId);
}
