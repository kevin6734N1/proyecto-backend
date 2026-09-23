package com.kevin.backend.repository;

import com.kevin.backend.model.Calibracion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CalibracionRepository extends JpaRepository<Calibracion, Long> {
    List<Calibracion> findByEvaluacionAptitudId(Long evaluacionAptitudId);
}
