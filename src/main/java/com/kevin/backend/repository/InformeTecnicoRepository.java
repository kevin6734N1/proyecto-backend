package com.kevin.backend.repository;

import com.kevin.backend.model.InformeTecnico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InformeTecnicoRepository extends JpaRepository<InformeTecnico, Long> {
    long countByNumeroStartingWith(String prefijo);
    boolean existsByRevisionTecnicaId(Long revisionTecnicaId);
    @Query("select count(i) > 0 from InformeTecnico i where i.revisionTecnica.calibracion.id = :calibracionId")
    boolean existsByCalibracionId(@Param("calibracionId") Long calibracionId);
}
