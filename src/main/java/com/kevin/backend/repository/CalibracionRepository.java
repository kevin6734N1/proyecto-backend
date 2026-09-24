package com.kevin.backend.repository;

import com.kevin.backend.model.Calibracion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.util.List;

public interface CalibracionRepository extends JpaRepository<Calibracion, Long> {
    List<Calibracion> findByEvaluacionAptitudId(Long evaluacionAptitudId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Calibracion c where c.id = :id")
    Optional<Calibracion> findByIdForUpdate(@Param("id") Long id);
}
