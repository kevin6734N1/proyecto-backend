package com.kevin.backend.repository;

import com.kevin.backend.model.RevisionTecnica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RevisionTecnicaRepository extends JpaRepository<RevisionTecnica, Long> {
    List<RevisionTecnica> findByCalibracionId(Long calibracionId);
}
