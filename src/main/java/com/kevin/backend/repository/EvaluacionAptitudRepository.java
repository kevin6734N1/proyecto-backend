package com.kevin.backend.repository;

import com.kevin.backend.model.EvaluacionAptitud;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EvaluacionAptitudRepository extends JpaRepository<EvaluacionAptitud, Long> {
    List<EvaluacionAptitud> findByOrdenDeTrabajoIdOrderByIdDesc(Long ordenDeTrabajoId);
    List<EvaluacionAptitud> findByInstrumentoId(Long instrumentoId);
}
