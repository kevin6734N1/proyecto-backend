package com.kevin.backend.service;

import com.kevin.backend.dto.*;
import com.kevin.backend.model.*;
import com.kevin.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gesminpdfsnapshot;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class PdfAutoPersistenciaTest {
    private static final Path CARPETA;
    static {
        try { CARPETA = Files.createTempDirectory("gesmin-pdf-snapshot-"); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    @DynamicPropertySource
    static void almacenamiento(DynamicPropertyRegistry registro) {
        registro.add("gesmin.pdf-generados-dir", () -> CARPETA.toString());
    }

    @Autowired private ClienteRepository clientes;
    @Autowired private ServicioRepository servicios;
    @Autowired private CotizacionService cotizaciones;
    @Autowired private OrdenDeTrabajoService ordenes;
    @Autowired private OrdenDeTrabajoRepository ordenesRepo;
    @Autowired private InstrumentoRepository instrumentos;
    @Autowired private EvaluacionAptitudRepository evaluaciones;
    @Autowired private CalibracionRepository calibraciones;
    @Autowired private RevisionTecnicaService revisiones;
    @Autowired private InformeTecnicoRepository informes;
    @Autowired private DocumentoGeneradoService pdf;

    @Test
    void creaTresSnapshotsTrasCommitYConservaElContenido() throws Exception {
        Cliente cliente = new Cliente();
        cliente.setRazonSocial("Cliente snapshot");
        cliente.setRuc("20123456789");
        cliente = clientes.saveAndFlush(cliente);
        Servicio servicio = new Servicio();
        servicio.setCodigo("SNAP-" + UUID.randomUUID());
        servicio.setNombre("Calibración");
        servicio.setTipoServicio(TipoServicio.CALIBRACION);
        servicio.setTipoEquipoAplicable("Multímetro");
        servicio.setPrecioVenta(new BigDecimal("100.00"));
        servicio = servicios.saveAndFlush(servicio);

        CotizacionDTO cot = cotizaciones.crear(new CotizacionDTO(null, null, cliente.getId(),
                null, null, null, null, null, LocalDate.now(), null, null, null,
                "Texto inicial", List.of(new DetalleCotizacionDTO(null, null, servicio.getId(),
                null, null, null, BigDecimal.ZERO, 1, null, null, null, null, null, null))));
        Path archivoCot = CARPETA.resolve("cotizacion-" + cot.id() + ".pdf");
        assertTrue(Files.isRegularFile(archivoCot));
        byte[] original = Files.readAllBytes(archivoCot);
        cotizaciones.actualizarEstado(cot.id(), EstadoCotizacion.APROBADA);
        assertArrayEquals(original, pdf.leerCotizacion(cot.id()));

        OrdenDeTrabajoDTO ot = ordenes.crear(new OrdenDeTrabajoDTO(null, null, cot.id(), null,
                null, null, "Técnico", null, "Laboratorio", LocalDate.now(), null, null,
                List.of(new DetalleOrdenTrabajoDTO(null, "Calibrar equipo", null, null, null, null))));
        Path archivoOt = CARPETA.resolve("orden-trabajo-" + ot.id() + ".pdf");
        assertTrue(Files.isRegularFile(archivoOt));
        ordenes.actualizarEstado(ot.id(), EstadoOrdenTrabajo.EN_PROCESO);
        assertArrayEquals(Files.readAllBytes(archivoOt), pdf.leerOrden(ot.id()));

        Instrumento instrumento = new Instrumento();
        instrumento.setCliente(cliente);
        instrumento.setTipo("Multímetro");
        instrumento.setMarca("Fluke");
        instrumento.setModelo("87V");
        instrumento.setSerie("SNAP-1");
        instrumento = instrumentos.saveAndFlush(instrumento);
        EvaluacionAptitud evaluacion = new EvaluacionAptitud();
        evaluacion.setOrdenDeTrabajo(ordenesRepo.findById(ot.id()).orElseThrow());
        evaluacion.setInstrumento(instrumento);
        evaluacion.setFechaEvaluacion(LocalDate.now());
        evaluacion.setResultado(ResultadoEvaluacion.APTO);
        evaluacion = evaluaciones.saveAndFlush(evaluacion);
        Calibracion cal = new Calibracion();
        cal.setEvaluacionAptitud(evaluacion);
        cal.setFecha(LocalDate.now());
        cal.setEstado(EstadoCalibracion.COMPLETADA);
        cal = calibraciones.saveAndFlush(cal);
        RevisionTecnicaDTO rev = revisiones.crear(new RevisionTecnicaDTO(null, cal.getId(),
                null, LocalDate.now(), "Revisor", null, null));
        revisiones.registrarResultado(rev.id(), ResultadoRevision.CONFORME, "Conforme");
        InformeTecnico informe = informes.findAll().stream()
                .filter(i -> i.getRevisionTecnica().getId().equals(rev.id()))
                .findFirst().orElseThrow();
        Path archivoIt = CARPETA.resolve("informe-tecnico-" + informe.getId() + "-sin-firma.pdf");
        assertTrue(Files.isRegularFile(archivoIt));
        assertArrayEquals(Files.readAllBytes(archivoIt), pdf.leerInforme(informe.getId()));
        revisiones.registrarResultado(rev.id(), ResultadoRevision.CONFORME, "Conforme");
        assertArrayEquals(Files.readAllBytes(archivoIt), pdf.leerInforme(informe.getId()));

        Files.delete(archivoOt);
        assertEquals("%PDF-", new String(pdf.leerOrden(ot.id()), 0, 5));
        System.out.println("### PDF snapshots: COI=" + archivoCot.getFileName()
                + " OT=" + archivoOt.getFileName() + " IT=" + archivoIt.getFileName()
                + "; estados posteriores e idempotencia conservaron bytes; fallback OT=PDF");
    }
}
