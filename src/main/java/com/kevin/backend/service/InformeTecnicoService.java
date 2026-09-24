package com.kevin.backend.service;

import com.kevin.backend.dto.InformeTecnicoDTO;
import com.kevin.backend.model.EstadoInforme;
import com.kevin.backend.model.InformeTecnico;
import com.kevin.backend.model.Instrumento;
import com.kevin.backend.model.RevisionTecnica;
import com.kevin.backend.repository.InformeTecnicoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class InformeTecnicoService {

    private final InformeTecnicoRepository informeTecnicoRepository;
    private final InformeFirmadoService firmados;

    public InformeTecnicoService(InformeTecnicoRepository informeTecnicoRepository,
                                 InformeFirmadoService firmados) {
        this.informeTecnicoRepository = informeTecnicoRepository;
        this.firmados = firmados;
    }

    public List<InformeTecnicoDTO> listar() {
        return informeTecnicoRepository.findAll().stream().map(this::toDTO).toList();
    }

    public InformeTecnicoDTO obtenerPorId(Long id) {
        return toDTO(buscarEntidadPorId(id));
    }

    @Transactional
    public InformeTecnico generarDesdeRevision(RevisionTecnica revision) {
        InformeTecnico informe = new InformeTecnico();
        informe.setRevisionTecnica(revision);
        informe.setFechaEmision(LocalDate.now());
        informe.setEstado(EstadoInforme.GENERADO);
        informe.setPdfCargado(false);
        informe.setNumero(generarNumero());
        return informeTecnicoRepository.save(informe);
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
        LocalDate hoy = LocalDate.now();
        String yy = String.format("%02d", hoy.getYear() % 100);
        String mm = String.format("%02d", hoy.getMonthValue());
        String prefijoAnual = "IT" + yy;

        long correlativo = informeTecnicoRepository.countByNumeroStartingWith(prefijoAnual) + 1;
        return "IT" + yy + mm + String.format("%02d", correlativo);
    }

    private InformeTecnico buscarEntidadPorId(Long id) {
        return informeTecnicoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Informe Técnico no encontrado con id " + id));
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
                i.getFechaEnvio()
        );
    }
}
