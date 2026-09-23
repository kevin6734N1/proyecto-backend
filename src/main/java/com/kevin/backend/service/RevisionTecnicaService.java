package com.kevin.backend.service;

import com.kevin.backend.dto.RevisionTecnicaDTO;
import com.kevin.backend.model.Calibracion;
import com.kevin.backend.model.EstadoCalibracion;
import com.kevin.backend.model.Instrumento;
import com.kevin.backend.model.ResultadoRevision;
import com.kevin.backend.model.RevisionTecnica;
import com.kevin.backend.repository.CalibracionRepository;
import com.kevin.backend.repository.RevisionTecnicaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class RevisionTecnicaService {

    private final RevisionTecnicaRepository revisionRepository;
    private final CalibracionRepository calibracionRepository;
    private final InformeTecnicoService informeTecnicoService;

    public RevisionTecnicaService(RevisionTecnicaRepository revisionRepository,
                                   CalibracionRepository calibracionRepository,
                                   InformeTecnicoService informeTecnicoService) {
        this.revisionRepository = revisionRepository;
        this.calibracionRepository = calibracionRepository;
        this.informeTecnicoService = informeTecnicoService;
    }

    public List<RevisionTecnicaDTO> listarPorCalibracion(Long calibracionId) {
        return revisionRepository.findByCalibracionId(calibracionId).stream().map(this::toDTO).toList();
    }

    public RevisionTecnicaDTO obtenerPorId(Long id) {
        return toDTO(buscarEntidadPorId(id));
    }

    @Transactional
    public RevisionTecnicaDTO crear(RevisionTecnicaDTO dto) {
        Calibracion calibracion = calibracionRepository.findById(dto.calibracionId())
                .orElseThrow(() -> new RuntimeException("Calibración no encontrada con id " + dto.calibracionId()));

        if (calibracion.getEstado() != EstadoCalibracion.COMPLETADA) {
            throw new IllegalArgumentException(
                "No se puede revisar: la calibración debe estar COMPLETADA (estado actual: " + calibracion.getEstado() + ")."
            );
        }

        RevisionTecnica revision = new RevisionTecnica();
        revision.setCalibracion(calibracion);
        revision.setFechaRevision(dto.fechaRevision() != null ? dto.fechaRevision() : LocalDate.now());
        revision.setRevisor(dto.revisor());
        revision.setResultado(ResultadoRevision.PENDIENTE);
        revision.setObservaciones(dto.observaciones());

        return toDTO(revisionRepository.save(revision));
    }

    /**
     * Registra el resultado de la revisión.
     * NO_CONFORME -> la Calibracion vuelve a EN_PROCESO para corregir (misma calibración, sin crear una nueva).
     * CONFORME -> se genera automáticamente el InformeTecnico (certificado) con correlativo.
     */
    @Transactional
    public RevisionTecnicaDTO registrarResultado(Long id, ResultadoRevision resultado, String observaciones) {
        RevisionTecnica revision = buscarEntidadPorId(id);
        revision.setResultado(resultado);
        if (observaciones != null) {
            revision.setObservaciones(observaciones);
        }
        revisionRepository.save(revision);

        Calibracion calibracion = revision.getCalibracion();
        if (resultado == ResultadoRevision.NO_CONFORME) {
            calibracion.setEstado(EstadoCalibracion.EN_PROCESO);
            calibracionRepository.save(calibracion);
        } else if (resultado == ResultadoRevision.CONFORME) {
            informeTecnicoService.generarDesdeRevision(revision);
        }

        return toDTO(revision);
    }

    private RevisionTecnica buscarEntidadPorId(Long id) {
        return revisionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Revisión técnica no encontrada con id " + id));
    }

    private RevisionTecnicaDTO toDTO(RevisionTecnica r) {
        Instrumento instrumento = r.getCalibracion().getEvaluacionAptitud().getInstrumento();
        return new RevisionTecnicaDTO(
                r.getId(),
                r.getCalibracion().getId(),
                instrumento.getMarca() + " " + instrumento.getModelo() + " - Serie " + instrumento.getSerie(),
                r.getFechaRevision(),
                r.getRevisor(),
                r.getResultado(),
                r.getObservaciones()
        );
    }
}
