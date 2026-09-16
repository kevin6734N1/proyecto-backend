package com.kevin.backend.repository;

import com.kevin.backend.model.OrdenDeTrabajo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrdenDeTrabajoRepository extends JpaRepository<OrdenDeTrabajo, Long> {
    long countByNumeroStartingWith(String prefijo);
    List<OrdenDeTrabajo> findByCotizacionId(Long cotizacionId);
    List<OrdenDeTrabajo> findByExpedienteId(Long expedienteId);
}
