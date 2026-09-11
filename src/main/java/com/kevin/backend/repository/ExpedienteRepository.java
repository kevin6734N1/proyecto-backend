package com.kevin.backend.repository;

import com.kevin.backend.model.Expediente;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExpedienteRepository extends JpaRepository<Expediente, Long> {
    long countByNumeroStartingWith(String prefijo);
    List<Expediente> findByClienteId(Long clienteId);
}