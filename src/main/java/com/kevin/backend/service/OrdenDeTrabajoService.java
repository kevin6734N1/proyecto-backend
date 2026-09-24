package com.kevin.backend.service;

import com.kevin.backend.dto.DetalleOrdenTrabajoDTO;
import com.kevin.backend.dto.OrdenDeTrabajoDTO;
import com.kevin.backend.model.*;
import com.kevin.backend.repository.CotizacionRepository;
import com.kevin.backend.repository.OrdenDeTrabajoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrdenDeTrabajoService {

    private final OrdenDeTrabajoRepository ordenDeTrabajoRepository;
    private final CotizacionRepository cotizacionRepository;
    private final CorrelativoRetry correlativos;
    private final CorrelativoService contador;

    public OrdenDeTrabajoService(OrdenDeTrabajoRepository ordenDeTrabajoRepository,
                                  CotizacionRepository cotizacionRepository,
                                  CorrelativoRetry correlativos, CorrelativoService contador) {
        this.ordenDeTrabajoRepository = ordenDeTrabajoRepository;
        this.cotizacionRepository = cotizacionRepository;
        this.correlativos = correlativos;
        this.contador = contador;
    }

    public List<OrdenDeTrabajoDTO> listar() {
        return ordenDeTrabajoRepository.findAll().stream().map(this::toDTO).toList();
    }

    public OrdenDeTrabajoDTO obtenerPorId(Long id) {
        return toDTO(buscarEntidadPorId(id));
    }

    public OrdenDeTrabajoDTO crear(OrdenDeTrabajoDTO dto) {
        return correlativos.ejecutar(() -> crearUnaVez(dto));
    }

    private OrdenDeTrabajoDTO crearUnaVez(OrdenDeTrabajoDTO dto) {
        Cotizacion cotizacion = cotizacionRepository.findById(dto.cotizacionId())
                .orElseThrow(() -> new RuntimeException("Cotización no encontrada con id " + dto.cotizacionId()));

        if (cotizacion.getEstado() != EstadoCotizacion.APROBADA) {
            throw new IllegalArgumentException(
                "No se puede generar una Orden de Trabajo: la cotización " + cotizacion.getCodigo() +
                " está en estado " + cotizacion.getEstado() + ", debe estar APROBADA."
            );
        }

        OrdenDeTrabajo orden = new OrdenDeTrabajo();
        orden.setCotizacion(cotizacion);
        orden.setExpediente(cotizacion.getExpediente());
        orden.setEjecutor(dto.ejecutor());
        orden.setArea(dto.area());
        orden.setLugar(dto.lugar());
        orden.setFecha(dto.fecha() != null ? dto.fecha() : LocalDate.now());
        orden.setHora(dto.hora());
        orden.setEstado(EstadoOrdenTrabajo.PENDIENTE);
        orden.setNumero(generarNumero());

        List<DetalleOrdenTrabajo> detalles = new ArrayList<>();
        for (DetalleOrdenTrabajoDTO detalleDTO : dto.detalles()) {
            DetalleOrdenTrabajo detalle = new DetalleOrdenTrabajo();
            detalle.setOrdenDeTrabajo(orden);
            detalle.setActividad(detalleDTO.actividad());
            detalle.setEvaluacionInicial(detalleDTO.evaluacionInicial());
            detalle.setConclusiones(detalleDTO.conclusiones());
            detalle.setRecomendaciones(detalleDTO.recomendaciones());
            detalle.setCompletado(detalleDTO.completado() != null ? detalleDTO.completado() : false);
            detalles.add(detalle);
        }
        orden.setDetalles(detalles);

        OrdenDeTrabajo guardada = ordenDeTrabajoRepository.saveAndFlush(orden);
        return toDTO(guardada);
    }

    @Transactional
    public OrdenDeTrabajoDTO actualizarEstado(Long id, EstadoOrdenTrabajo nuevoEstado) {
        OrdenDeTrabajo orden = buscarEntidadPorId(id);
        orden.setEstado(nuevoEstado);
        return toDTO(ordenDeTrabajoRepository.save(orden));
    }

    private String generarNumero() {
        String yy = String.format("%02d", LocalDate.now().getYear() % 100);
        return contador.siguiente("OT", () -> ordenDeTrabajoRepository.countByNumeroStartingWith("OT" + yy));
    }

    private OrdenDeTrabajo buscarEntidadPorId(Long id) {
        return ordenDeTrabajoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Orden de Trabajo no encontrada con id " + id));
    }

    private OrdenDeTrabajoDTO toDTO(OrdenDeTrabajo o) {
        List<DetalleOrdenTrabajoDTO> detallesDTO = o.getDetalles().stream()
                .map(d -> new DetalleOrdenTrabajoDTO(
                        d.getId(), d.getActividad(), d.getEvaluacionInicial(),
                        d.getConclusiones(), d.getRecomendaciones(), d.getCompletado()
                ))
                .toList();

        return new OrdenDeTrabajoDTO(
                o.getId(),
                o.getNumero(),
                o.getCotizacion().getId(),
                o.getCotizacion().getCodigo(),
                o.getExpediente() != null ? o.getExpediente().getId() : null,
                o.getExpediente() != null ? o.getExpediente().getNumero() : null,
                o.getEjecutor(),
                o.getArea(),
                o.getLugar(),
                o.getFecha(),
                o.getHora(),
                o.getEstado(),
                detallesDTO
        );
    }
}
