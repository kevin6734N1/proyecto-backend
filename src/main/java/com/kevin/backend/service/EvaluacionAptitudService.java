package com.kevin.backend.service;

import com.kevin.backend.dto.EvaluacionAptitudDTO;
import com.kevin.backend.model.*;
import com.kevin.backend.repository.EvaluacionAptitudRepository;
import com.kevin.backend.repository.InstrumentoRepository;
import com.kevin.backend.repository.OrdenDeTrabajoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class EvaluacionAptitudService {

    private final EvaluacionAptitudRepository evaluacionRepository;
    private final OrdenDeTrabajoRepository ordenDeTrabajoRepository;
    private final InstrumentoRepository instrumentoRepository;

    public EvaluacionAptitudService(EvaluacionAptitudRepository evaluacionRepository,
                                     OrdenDeTrabajoRepository ordenDeTrabajoRepository,
                                     InstrumentoRepository instrumentoRepository) {
        this.evaluacionRepository = evaluacionRepository;
        this.ordenDeTrabajoRepository = ordenDeTrabajoRepository;
        this.instrumentoRepository = instrumentoRepository;
    }

    public List<EvaluacionAptitudDTO> listarPorOrden(Long ordenDeTrabajoId) {
        return evaluacionRepository.findByOrdenDeTrabajoIdOrderByIdDesc(ordenDeTrabajoId)
                .stream().map(this::toDTO).toList();
    }

    public EvaluacionAptitudDTO obtenerPorId(Long id) {
        return toDTO(buscarEntidadPorId(id));
    }

    @Transactional
    public EvaluacionAptitudDTO crear(EvaluacionAptitudDTO dto) {
        OrdenDeTrabajo orden = ordenDeTrabajoRepository.findById(dto.ordenDeTrabajoId())
                .orElseThrow(() -> new IllegalArgumentException("Orden de Trabajo no encontrada con id " + dto.ordenDeTrabajoId()));
        Instrumento instrumento = instrumentoRepository.findById(dto.instrumentoId())
                .orElseThrow(() -> new IllegalArgumentException("Instrumento no encontrado con id " + dto.instrumentoId()));

        EvaluacionAptitud evaluacion = new EvaluacionAptitud();
        evaluacion.setOrdenDeTrabajo(orden);
        evaluacion.setInstrumento(instrumento);
        evaluacion.setFechaEvaluacion(dto.fechaEvaluacion() != null ? dto.fechaEvaluacion() : LocalDate.now());
        evaluacion.setResultado(ResultadoEvaluacion.PENDIENTE);
        evaluacion.setObservaciones(dto.observaciones());

        return toDTO(evaluacionRepository.save(evaluacion));
    }

    /**
     * Registra el resultado de la evaluación (APTO/NO_APTO).
     * Si es NO_APTO, la OT pasa a EN_ESPERA_CLIENTE (se detiene hasta que el cliente responda).
     * Si es APTO, la OT pasa a EN_PROCESO (lista para programar calibración).
     */
    @Transactional
    public EvaluacionAptitudDTO registrarResultado(Long id, ResultadoEvaluacion resultado, String observaciones) {
        EvaluacionAptitud evaluacion = buscarEntidadPorId(id);
        evaluacion.setResultado(resultado);
        if (observaciones != null) {
            evaluacion.setObservaciones(observaciones);
        }

        OrdenDeTrabajo orden = evaluacion.getOrdenDeTrabajo();
        if (resultado == ResultadoEvaluacion.NO_APTO) {
            TransicionesEstado.validarTransicion(orden.getEstado(), EstadoOrdenTrabajo.EN_ESPERA_CLIENTE,
                    TransicionesEstado.ORDEN_INTERNA);
            orden.setEstado(EstadoOrdenTrabajo.EN_ESPERA_CLIENTE);
        } else if (resultado == ResultadoEvaluacion.APTO) {
            TransicionesEstado.validarTransicion(orden.getEstado(), EstadoOrdenTrabajo.EN_PROCESO,
                    TransicionesEstado.ORDEN_INTERNA);
            orden.setEstado(EstadoOrdenTrabajo.EN_PROCESO);
        }
        ordenDeTrabajoRepository.save(orden);

        return toDTO(evaluacionRepository.save(evaluacion));
    }

    /**
     * Registra la respuesta del cliente tras la comunicación por un instrumento NO_APTO.
     * Si autoriza continuar -> se crea una NUEVA evaluación (vuelve al loop de evaluación).
     * Si no autoriza -> se cancela la Orden de Trabajo.
     */
    @Transactional
    public EvaluacionAptitudDTO registrarRespuestaCliente(Long id, boolean autorizaContinuar, String comunicacionCliente) {
        EvaluacionAptitud evaluacion = buscarEntidadPorId(id);

        if (evaluacion.getResultado() != ResultadoEvaluacion.NO_APTO) {
            throw new IllegalArgumentException(
                "Solo se puede registrar respuesta del cliente sobre una evaluación con resultado NO_APTO."
            );
        }

        evaluacion.setComunicacionCliente(comunicacionCliente);
        evaluacion.setAutorizaContinuar(autorizaContinuar);
        evaluacionRepository.save(evaluacion);

        OrdenDeTrabajo orden = evaluacion.getOrdenDeTrabajo();

        if (autorizaContinuar) {
            // Loop: se crea una nueva evaluación pendiente para el mismo instrumento
            EvaluacionAptitud nuevaEvaluacion = new EvaluacionAptitud();
            nuevaEvaluacion.setOrdenDeTrabajo(orden);
            nuevaEvaluacion.setInstrumento(evaluacion.getInstrumento());
            nuevaEvaluacion.setFechaEvaluacion(LocalDate.now());
            nuevaEvaluacion.setResultado(ResultadoEvaluacion.PENDIENTE);
            evaluacionRepository.save(nuevaEvaluacion);

            TransicionesEstado.validarTransicion(orden.getEstado(), EstadoOrdenTrabajo.PENDIENTE,
                    TransicionesEstado.ORDEN_INTERNA);
            orden.setEstado(EstadoOrdenTrabajo.PENDIENTE); // vuelve a estar lista para re-evaluar
            ordenDeTrabajoRepository.save(orden);

            return toDTO(nuevaEvaluacion);
        } else {
            TransicionesEstado.validarTransicion(orden.getEstado(), EstadoOrdenTrabajo.CANCELADA,
                    TransicionesEstado.ORDEN_INTERNA);
            orden.setEstado(EstadoOrdenTrabajo.CANCELADA);
            ordenDeTrabajoRepository.save(orden);
            return toDTO(evaluacion);
        }
    }

    private EvaluacionAptitud buscarEntidadPorId(Long id) {
        return evaluacionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Evaluación no encontrada con id " + id));
    }

    private EvaluacionAptitudDTO toDTO(EvaluacionAptitud e) {
        Instrumento i = e.getInstrumento();
        return new EvaluacionAptitudDTO(
                e.getId(),
                e.getOrdenDeTrabajo().getId(),
                e.getOrdenDeTrabajo().getNumero(),
                i.getId(),
                i.getMarca() + " " + i.getModelo() + " - Serie " + i.getSerie(),
                e.getFechaEvaluacion(),
                e.getResultado(),
                e.getObservaciones(),
                e.getComunicacionCliente(),
                e.getAutorizaContinuar()
        );
    }
}
