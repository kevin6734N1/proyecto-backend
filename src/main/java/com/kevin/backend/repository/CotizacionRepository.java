package com.kevin.backend.repository;

import com.kevin.backend.model.Cotizacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CotizacionRepository extends JpaRepository<Cotizacion, Long> {
    long countByCodigoStartingWith(String prefijo);
    List<Cotizacion> findByClienteId(Long clienteId);
    List<Cotizacion> findByExpedienteId(Long expedienteId);
}
