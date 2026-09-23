package com.kevin.backend.service;

import com.kevin.backend.dto.CalibracionDTO;
import com.kevin.backend.dto.PuntoCalibracionDTO;
import com.kevin.backend.model.Calibracion;
import com.kevin.backend.model.EstadoCalibracion;
import com.kevin.backend.model.EvaluacionAptitud;
import com.kevin.backend.model.Herramienta;
import com.kevin.backend.model.Instrumento;
import com.kevin.backend.model.PuntoCalibracion;
import com.kevin.backend.model.ResultadoEvaluacion;
import com.kevin.backend.repository.CalibracionRepository;
import com.kevin.backend.repository.EvaluacionAptitudRepository;
import com.kevin.backend.repository.HerramientaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class CalibracionService {

    private final CalibracionRepository calibracionRepository;
    private final EvaluacionAptitudRepository evaluacionAptitudRepository;
    private final HerramientaRepository herramientaRepository;

    public CalibracionService(CalibracionRepository calibracionRepository,
                               EvaluacionAptitudRepository evaluacionAptitudRepository,
                               HerramientaRepository herramientaRepository) {
        this.calibracionRepository = calibracionRepository;
        this.evaluacionAptitudRepository = evaluacionAptitudRepository;
        this.herramientaRepository = herramientaRepository;
    }

    public List<CalibracionDTO> listar() {
        return calibracionRepository.findAll().stream().map(this::toDTO).toList();
    }

    public CalibracionDTO obtenerPorId(Long id) {
        return toDTO(buscarEntidadPorId(id));
    }

    @Transactional
    public CalibracionDTO crear(CalibracionDTO dto) {
        EvaluacionAptitud evaluacion = evaluacionAptitudRepository.findById(dto.evaluacionAptitudId())
                .orElseThrow(() -> new RuntimeException("Evaluación no encontrada con id " + dto.evaluacionAptitudId()));

        if (evaluacion.getResultado() != ResultadoEvaluacion.APTO) {
            throw new IllegalArgumentException(
                "No se puede programar una calibración: la evaluación de aptitud debe tener resultado APTO."
            );
        }

        Calibracion calibracion = new Calibracion();
        calibracion.setEvaluacionAptitud(evaluacion);
        calibracion.setTecnico(dto.tecnico());
        calibracion.setProcedimiento(dto.procedimiento());
        calibracion.setFecha(dto.fecha());
        calibracion.setEstado(EstadoCalibracion.PROGRAMADA);
        calibracion.setPatrones(buscarPatrones(dto.patronesIds()));

        Calibracion guardada = calibracionRepository.save(calibracion);
        return toDTO(guardada);
    }

    @Transactional
    public CalibracionDTO actualizarEstado(Long id, EstadoCalibracion nuevoEstado) {
        Calibracion calibracion = buscarEntidadPorId(id);
        calibracion.setEstado(nuevoEstado);
        return toDTO(calibracionRepository.save(calibracion));
    }

    /**
     * Registra los puntos de medición de la calibración (reemplaza los existentes).
     * Se usa al ejecutar la calibración, cuando el técnico ya tiene los valores reales.
     */
    @Transactional
    public CalibracionDTO registrarMediciones(Long id, List<PuntoCalibracionDTO> puntosDTO) {
        Calibracion calibracion = buscarEntidadPorId(id);
        calibracion.getPuntos().clear();

        for (PuntoCalibracionDTO p : puntosDTO) {
            PuntoCalibracion punto = new PuntoCalibracion();
            punto.setCalibracion(calibracion);
            punto.setPuntoDescripcion(p.puntoDescripcion());
            punto.setValorPatron(p.valorPatron());
            punto.setValorMedido(p.valorMedido());
            punto.setError(p.valorMedido().subtract(p.valorPatron()));
            punto.setUnidad(p.unidad());
            punto.setDentroDeTolerancia(p.dentroDeTolerancia());
            calibracion.getPuntos().add(punto);
        }

        calibracion.setEstado(EstadoCalibracion.EN_PROCESO);
        return toDTO(calibracionRepository.save(calibracion));
    }

    private List<Herramienta> buscarPatrones(List<Long> ids) {
        List<Herramienta> patrones = new ArrayList<>();
        for (Long id : ids) {
            Herramienta h = herramientaRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Herramienta (patrón) no encontrada con id " + id));
            patrones.add(h);
        }
        return patrones;
    }

    private Calibracion buscarEntidadPorId(Long id) {
        return calibracionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Calibración no encontrada con id " + id));
    }

    private CalibracionDTO toDTO(Calibracion c) {
        List<String> patronesDesc = c.getPatrones().stream()
                .map(h -> h.getCodigoInterno() + " - " + h.getDescripcion())
                .toList();
        List<Long> patronesIds = c.getPatrones().stream().map(Herramienta::getId).toList();

        List<PuntoCalibracionDTO> puntosDTO = c.getPuntos().stream()
                .map(p -> new PuntoCalibracionDTO(
                        p.getId(), p.getPuntoDescripcion(), p.getValorPatron(),
                        p.getValorMedido(), p.getError(), p.getUnidad(), p.getDentroDeTolerancia()
                ))
                .toList();

        Instrumento instrumento = c.getEvaluacionAptitud().getInstrumento();

        return new CalibracionDTO(
                c.getId(),
                c.getEvaluacionAptitud().getId(),
                instrumento.getMarca() + " " + instrumento.getModelo() + " - Serie " + instrumento.getSerie(),
                c.getTecnico(),
                c.getProcedimiento(),
                c.getFecha(),
                c.getEstado(),
                patronesIds,
                patronesDesc,
                puntosDTO
        );
    }
}
