package com.kevin.backend.repository;

import com.kevin.backend.model.InformeTecnico;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InformeTecnicoRepository extends JpaRepository<InformeTecnico, Long> {
    long countByNumeroStartingWith(String prefijo);
}
