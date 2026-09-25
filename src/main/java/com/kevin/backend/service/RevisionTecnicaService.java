package com.kevin.backend.service;

import com.kevin.backend.dto.RevisionTecnicaDTO;
import com.kevin.backend.model.Calibracion;
import com.kevin.backend.model.EstadoCalibracion;
import com.kevin.backend.model.EstadoInforme;
import com.kevin.backend.model.Instrumento;
import com.kevin.backend.model.ResultadoRevision;
import com.kevin.backend.model.RevisionTecnica;
import com.kevin.backend.repository.CalibracionRepository;
import com.kevin.backend.repository.InformeTecnicoRepository;
import com.kevin.backend.repository.RevisionTecnicaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
public class RevisionTecnicaService {

    private final RevisionTecnicaRepository revisionRepository;
    private final CalibracionRepository calibracionRepository;
    private final InformeTecnicoRepository informeRepository;
    private final InformeTecnicoService informeTecnicoService;
    private final CorrelativoRetry correlativos;
    private final DocumentoGeneradoService pdfGenerados;

    public RevisionTecnicaService(RevisionTecnicaRepository revisionRepository,
                                  CalibracionRepository calibracionRepository,
                                  InformeTecnicoRepository informeRepository,
                                  InformeTecnicoService informeTecnicoService,
                                  CorrelativoRetry correlativos,
                                  DocumentoGeneradoService pdfGenerados) {
        this.revisionRepository = revisionRepository;
        this.calibracionRepository = calibracionRepository;
        this.informeRepository = informeRepository;
        this.informeTecnicoService = informeTecnicoService;
        this.correlativos = correlativos;
        this.pdfGenerados = pdfGenerados;
    }

    public List<RevisionTecnicaDTO> listarPorCalibracion(Long calibracionId) {
        return revisionRepository.findByCalibracionId(calibracionId).stream().map(this::toDTO).toList();
    }

    public RevisionTecnicaDTO obtenerPorId(Long id) {
        return toDTO(buscarEntidadPorId(id));
    }

    @Transactional
    public RevisionTecnicaDTO crear(RevisionTecnicaDTO dto) {
        Calibracion calibracion = calibracionRepository.findByIdForUpdate(dto.calibracionId())
                .orElseThrow(() -> new RuntimeException("Calibración no encontrada con id " + dto.calibracionId()));

        if (calibracion.getEstado() != EstadoCalibracion.COMPLETADA) {
            throw new IllegalArgumentException(
                    "No se puede revisar: la calibración debe estar COMPLETADA (estado actual: "
                            + calibracion.getEstado() + ").");
        }
        // Un informe generado ya representa el resultado conforme de esta calibración.
        // Una reemisión requiere un flujo explícito, no otra revisión sobre el mismo trabajo.
        if (informeRepository.existsActivoByCalibracionId(calibracion.getId(), EstadoInforme.ANULADO)) {
            throw new IllegalArgumentException(
                    "No se puede crear otra revisión: esta calibración ya tiene un informe técnico.");
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
     * El reintento abarca resultado de revisión + efecto sobre calibración + informe.
     * Si colisiona el correlativo IT, todo se revierte antes del siguiente intento.
     */
    public RevisionTecnicaDTO registrarResultado(Long id, ResultadoRevision resultado, String observaciones) {
        ResultadoRegistro registro = correlativos.ejecutar(
                () -> registrarResultadoUnaVez(id, resultado, observaciones));
        // El ID proviene del intento confirmado; una llamada idempotente no tiene informe nuevo.
        if (registro.informeNuevoId() != null) {
            pdfGenerados.guardarInforme(registro.informeNuevoId());
        }
        return registro.revision();
    }

    private record ResultadoRegistro(RevisionTecnicaDTO revision, Long informeNuevoId) {}

    private ResultadoRegistro registrarResultadoUnaVez(Long id, ResultadoRevision resultado, String observaciones) {
        Long calibracionId = revisionRepository.findCalibracionIdById(id)
                .orElseThrow(() -> new RuntimeException("Revisión técnica no encontrada con id " + id));
        Calibracion calibracion = calibracionRepository.findByIdForUpdate(calibracionId)
                .orElseThrow(() -> new RuntimeException("Calibración no encontrada con id " + calibracionId));
        RevisionTecnica revision = revisionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("Revisión técnica no encontrada con id " + id));

        if (resultado == ResultadoRevision.PENDIENTE) {
            throw new IllegalArgumentException("No se puede volver a PENDIENTE.");
        }
        String observacionFinal = observaciones != null ? observaciones : revision.getObservaciones();
        boolean mismoResultado = revision.getResultado() == resultado;
        if (mismoResultado && Objects.equals(revision.getObservaciones(), observacionFinal)) {
            return new ResultadoRegistro(toDTO(revision), null);
        }

        // Otra revisión pendiente no puede invalidar un certificado emitido por un revisor distinto.
        boolean informeAjeno = informeRepository
                .findActivosByCalibracionId(calibracionId, EstadoInforme.ANULADO).stream()
                .anyMatch(i -> !i.getRevisionTecnica().getId().equals(id));
        if (informeAjeno) {
            throw new IllegalArgumentException(
                    "Hay un informe vigente de otra revisión; primero debe resolverse esa revisión.");
        }

        if (resultado == ResultadoRevision.CONFORME
                && calibracion.getEstado() != EstadoCalibracion.COMPLETADA) {
            throw new IllegalArgumentException("La calibración debe estar COMPLETADA para certificar.");
        }
        if (revision.getResultado() == ResultadoRevision.PENDIENTE
                && calibracion.getEstado() != EstadoCalibracion.COMPLETADA) {
            throw new IllegalArgumentException("La calibración debe estar COMPLETADA para registrar la revisión.");
        }
        if (revision.getResultado() == ResultadoRevision.CONFORME
                && resultado == ResultadoRevision.NO_CONFORME
                && (observaciones == null || observaciones.isBlank())) {
            throw new IllegalArgumentException("Indique la observación que motiva la reapertura.");
        }

        if (revision.getResultado() == ResultadoRevision.CONFORME) {
            String motivo = resultado == ResultadoRevision.NO_CONFORME
                    ? "Reapertura por NO_CONFORME: " + observaciones
                    : "Reemisión por cambio de observaciones: " + observaciones;
            informeTecnicoService.anularActivos(calibracionId, id, motivo);
        }

        revision.setResultado(resultado);
        if (observaciones != null) revision.setObservaciones(observaciones);
        revisionRepository.saveAndFlush(revision);

        Long informeNuevoId = null;
        if (resultado == ResultadoRevision.NO_CONFORME) {
            TransicionesEstado.validarTransicion(calibracion.getEstado(), EstadoCalibracion.EN_PROCESO,
                    TransicionesEstado.CALIBRACION);
            calibracion.setEstado(EstadoCalibracion.EN_PROCESO);
            calibracionRepository.save(calibracion);
        } else if (resultado == ResultadoRevision.CONFORME) {
            informeNuevoId = informeTecnicoService.generarDesdeRevision(revision).getId();
        }
        return new ResultadoRegistro(toDTO(revision), informeNuevoId);
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
