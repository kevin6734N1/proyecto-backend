package com.kevin.backend.service;

import com.kevin.backend.dto.EvaluacionAptitudDTO;
import com.kevin.backend.model.*;
import com.kevin.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gesminestados;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class FlujoEstadosServiceTest {
    @Autowired private ClienteRepository clientes;
    @Autowired private CotizacionRepository cotizaciones;
    @Autowired private OrdenDeTrabajoRepository ordenes;
    @Autowired private InstrumentoRepository instrumentos;
    @Autowired private EvaluacionAptitudRepository evaluaciones;
    @Autowired private CalibracionRepository calibraciones;
    @Autowired private EvaluacionAptitudService evaluacionService;
    @Autowired private OrdenDeTrabajoService ordenService;
    @Autowired private CalibracionService calibracionService;
    @Autowired private ExpedienteService expedienteService;

    @Test
    void loopNoAptoUsaTablaInternaYPatchRespetaTerminales() {
        Cliente cliente = new Cliente();
        cliente.setRazonSocial("Cliente estados");
        cliente.setRuc("20987654321");
        cliente = clientes.saveAndFlush(cliente);
        Cotizacion cot = new Cotizacion();
        cot.setCliente(cliente);
        cot.setCodigo("EST-" + UUID.randomUUID());
        cot.setFechaEmision(LocalDate.now());
        cot.setEstado(EstadoCotizacion.APROBADA);
        cot.setMontoTotal(BigDecimal.ZERO);
        cot = cotizaciones.saveAndFlush(cot);
        OrdenDeTrabajo ot = new OrdenDeTrabajo();
        ot.setCotizacion(cot);
        ot.setNumero("EST-" + UUID.randomUUID());
        ot.setFecha(LocalDate.now());
        ot.setEstado(EstadoOrdenTrabajo.PENDIENTE);
        ot = ordenes.saveAndFlush(ot);
        Instrumento instrumento = new Instrumento();
        instrumento.setCliente(cliente);
        instrumento.setTipo("Multímetro");
        instrumento.setMarca("Fluke");
        instrumento.setModelo("87V");
        instrumento.setSerie("EST-1");
        instrumento = instrumentos.saveAndFlush(instrumento);
        EvaluacionAptitud evaluacion = new EvaluacionAptitud();
        evaluacion.setOrdenDeTrabajo(ot);
        evaluacion.setInstrumento(instrumento);
        evaluacion.setFechaEvaluacion(LocalDate.now());
        evaluacion.setResultado(ResultadoEvaluacion.PENDIENTE);
        evaluacion = evaluaciones.saveAndFlush(evaluacion);

        evaluacionService.registrarResultado(evaluacion.getId(), ResultadoEvaluacion.NO_APTO, "Reparar");
        assertEquals(EstadoOrdenTrabajo.EN_ESPERA_CLIENTE, ordenes.findById(ot.getId()).orElseThrow().getEstado());
        Long otId = ot.getId();
        assertThrows(IllegalArgumentException.class,
                () -> ordenService.actualizarEstado(otId, EstadoOrdenTrabajo.PENDIENTE));
        EvaluacionAptitudDTO nueva = evaluacionService.registrarRespuestaCliente(evaluacion.getId(), true, "Autoriza");
        assertEquals(EstadoOrdenTrabajo.PENDIENTE, ordenes.findById(otId).orElseThrow().getEstado());
        evaluacionService.registrarResultado(nueva.id(), ResultadoEvaluacion.APTO, null);
        assertEquals(EstadoOrdenTrabajo.EN_PROCESO, ordenes.findById(otId).orElseThrow().getEstado());
        ordenService.actualizarEstado(otId, EstadoOrdenTrabajo.COMPLETADA);
        assertThrows(IllegalArgumentException.class,
                () -> ordenService.actualizarEstado(otId, EstadoOrdenTrabajo.CANCELADA));

        Calibracion cal = new Calibracion();
        cal.setEvaluacionAptitud(evaluaciones.findById(nueva.id()).orElseThrow());
        cal.setFecha(LocalDate.now());
        cal.setEstado(EstadoCalibracion.COMPLETADA);
        cal = calibraciones.saveAndFlush(cal);
        calibracionService.actualizarEstado(cal.getId(), EstadoCalibracion.EN_PROCESO);
        assertEquals(EstadoCalibracion.EN_PROCESO, calibraciones.findById(cal.getId()).orElseThrow().getEstado());

        Long expId = expedienteService.crear(cliente.getId()).id();
        expedienteService.cambiarEstado(expId, EstadoExpediente.CERRADO);
        assertThrows(IllegalArgumentException.class,
                () -> expedienteService.cambiarEstado(expId, EstadoExpediente.EN_PROCESO));
        System.out.println("### Estados: NO_APTO→EN_ESPERA_CLIENTE; PATCH→PENDIENTE bloqueado; "
                + "respuesta cliente→PENDIENTE; APTO→EN_PROCESO; COMPLETADA y CERRADO terminales");
    }
}
