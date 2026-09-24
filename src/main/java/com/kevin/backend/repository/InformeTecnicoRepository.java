package com.kevin.backend.repository;

import com.kevin.backend.model.InformeTecnico;
import com.kevin.backend.model.EstadoInforme;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InformeTecnicoRepository extends JpaRepository<InformeTecnico, Long> {
    long countByNumeroStartingWith(String prefijo);
    boolean existsByRevisionTecnicaId(Long revisionTecnicaId);
    boolean existsByRevisionTecnicaIdAndEstadoNot(Long revisionTecnicaId, EstadoInforme estado);

    @Query("select i from InformeTecnico i where i.revisionTecnica.calibracion.id = :calibracionId and i.estado <> :anulado")
    List<InformeTecnico> findActivosByCalibracionId(@Param("calibracionId") Long calibracionId,
                                                     @Param("anulado") EstadoInforme anulado);

    @Query("select count(i) > 0 from InformeTecnico i where i.revisionTecnica.calibracion.id = :calibracionId and i.estado <> :anulado")
    boolean existsActivoByCalibracionId(@Param("calibracionId") Long calibracionId,
                                        @Param("anulado") EstadoInforme anulado);
}
