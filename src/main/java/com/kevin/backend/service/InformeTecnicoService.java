package com.kevin.backend.service;

import com.kevin.backend.dto.InformeTecnicoDTO;
import com.kevin.backend.model.EstadoInforme;
import com.kevin.backend.model.InformeTecnico;
import com.kevin.backend.model.Instrumento;
import com.kevin.backend.model.RevisionTecnica;
import com.kevin.backend.repository.InformeTecnicoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDate;
import java.util.List;

@Service
public class InformeTecnicoService {

    private final InformeTecnicoRepository informeTecnicoRepository;
    private final InformeFirmadoService firmados;
    private final CorrelativoService contador;

    public InformeTecnicoService(InformeTecnicoRepository informeTecnicoRepository,
                                 InformeFirmadoService firmados, CorrelativoService contador) {
        this.informeTecnicoRepository = informeTecnicoRepository;
        this.firmados = firmados;
        this.contador = contador;
    }

    public List<InformeTecnicoDTO> listar() {
        return informeTecnicoRepository.findAll().stream().map(this::toDTO).toList();
    }

    public InformeTecnicoDTO obtenerPorId(Long id) {
        return toDTO(buscarEntidadPorId(id));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public InformeTecnico generarDesdeRevision(RevisionTecnica revision) {
        if (informeTecnicoRepository.existsByRevisionTecnicaIdAndEstadoNot(revision.getId(), EstadoInforme.ANULADO)) {
            throw new IllegalArgumentException("Esta revisión ya tiene un informe técnico.");
        }
        InformeTecnico informe = new InformeTecnico();
        informe.setRevisionTecnica(revision);
        informe.setFechaEmision(LocalDate.now());
        informe.setEstado(EstadoInforme.GENERADO);
        informe.setPdfCargado(false);
        informe.setNumero(generarNumero());
        return informeTecnicoRepository.saveAndFlush(informe);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void anularActivos(Long calibracionId, Long revisionId, String motivo) {
        for (InformeTecnico informe : informeTecnicoRepository
                .findActivosByCalibracionId(calibracionId, EstadoInforme.ANULADO)) {
            if (!informe.getRevisionTecnica().getId().equals(revisionId)) {
                throw new IllegalArgumentException(
                        "Hay un informe vigente de otra revisión; primero debe resolverse esa revisión.");
            }
            informe.setEstado(EstadoInforme.ANULADO);
            informe.setFechaAnulacion(LocalDate.now());
            informe.setMotivoAnulacion(motivo);
            informeTecnicoRepository.saveAndFlush(informe);
        }
    }

    @Transactional
    public InformeTecnicoDTO actualizarEstado(Long id, EstadoInforme nuevoEstado) {
        InformeTecnico informe = buscarEntidadPorId(id);
        if (informe.getEstado() == nuevoEstado) {
            return toDTO(informe);
        }
        boolean aprobar = informe.getEstado() == EstadoInforme.PDF_CARGADO
                && nuevoEstado == EstadoInforme.APROBADO;
        boolean enviar = informe.getEstado() == EstadoInforme.APROBADO
                && nuevoEstado == EstadoInforme.ENVIADO;
        if (!aprobar && !enviar) {
            throw new IllegalArgumentException("Transición de informe no permitida: "
                    + informe.getEstado() + " -> " + nuevoEstado
                    + ". Primero cargue el PDF firmado, luego apruebe y finalmente marque ENVIADO.");
        }
        if (aprobar && (!Boolean.TRUE.equals(informe.getPdfCargado()) || !firmados.existe(id))) {
            throw new IllegalArgumentException("No se puede aprobar: falta el archivo PDF firmado.");
        }
        informe.setEstado(nuevoEstado);
        if (enviar) {
            informe.setFechaEnvio(LocalDate.now());
        }
        return toDTO(informeTecnicoRepository.save(informe));
    }

    private String generarNumero() {
        String yy = String.format("%02d", LocalDate.now().getYear() % 100);
        return contador.siguiente("IT", () -> informeTecnicoRepository.countByNumeroStartingWith("IT" + yy));
    }

    private InformeTecnico buscarEntidadPorId(Long id) {
        return informeTecnicoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Informe Técnico no encontrado con id " + id));
    }

    private InformeTecnicoDTO toDTO(InformeTecnico i) {
        Instrumento instrumento = i.getRevisionTecnica().getCalibracion().getEvaluacionAptitud().getInstrumento();
        return new InformeTecnicoDTO(
                i.getId(),
                i.getNumero(),
                i.getRevisionTecnica().getId(),
                instrumento.getMarca() + " " + instrumento.getModelo() + " - Serie " + instrumento.getSerie(),
                i.getFechaEmision(),
                i.getEstado(),
                i.getPdfCargado(),
                i.getFechaCargaPdf(),
                i.getFechaEnvio(),
                i.getFechaAnulacion(),
                i.getMotivoAnulacion()
        );
    }
}
