package com.kevin.backend.repository;

import com.kevin.backend.model.RevisionTecnica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.util.List;

public interface RevisionTecnicaRepository extends JpaRepository<RevisionTecnica, Long> {
    List<RevisionTecnica> findByCalibracionId(Long calibracionId);
    @Query("select r.calibracion.id from RevisionTecnica r where r.id = :id")
    Optional<Long> findCalibracionIdById(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RevisionTecnica r where r.id = :id")
    Optional<RevisionTecnica> findByIdForUpdate(@Param("id") Long id);
}
