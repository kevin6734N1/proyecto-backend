package com.kevin.backend.service;

import com.kevin.backend.controller.DocumentoPdfController;
import com.kevin.backend.dto.CotizacionDTO;
import com.kevin.backend.dto.DetalleCotizacionDTO;
import com.kevin.backend.dto.DetalleOrdenTrabajoDTO;
import com.kevin.backend.dto.ExpedienteDTO;
import com.kevin.backend.dto.OrdenDeTrabajoDTO;
import com.kevin.backend.dto.RevisionTecnicaDTO;
import com.kevin.backend.dto.ServicioDTO;
import com.kevin.backend.model.Calibracion;
import com.kevin.backend.model.Cliente;
import com.kevin.backend.model.Cotizacion;
import com.kevin.backend.model.EstadoCalibracion;
import com.kevin.backend.model.EstadoCotizacion;
import com.kevin.backend.model.EstadoOrdenTrabajo;
import com.kevin.backend.model.EvaluacionAptitud;
import com.kevin.backend.model.Instrumento;
import com.kevin.backend.model.OrdenDeTrabajo;
import com.kevin.backend.model.ResultadoEvaluacion;
import com.kevin.backend.model.ResultadoRevision;
import com.kevin.backend.model.TipoServicio;
import com.kevin.backend.repository.CalibracionRepository;
import com.kevin.backend.repository.ClienteRepository;
import com.kevin.backend.repository.CotizacionRepository;
import com.kevin.backend.repository.CorrelativoContadorRepository;
import com.kevin.backend.repository.EvaluacionAptitudRepository;
import com.kevin.backend.repository.ExpedienteRepository;
import com.kevin.backend.repository.InformeTecnicoRepository;
import com.kevin.backend.repository.InstrumentoRepository;
import com.kevin.backend.repository.OrdenDeTrabajoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Auditoría E11: hallazgos H9, H12 y H15 de HALLAZGOS_NUEVOS.md verificados
 * por ejecución sobre el código vigente (no por lectura del informe).
 * Base H2 en memoria; no toca ./data/gesmin ni los informes de desarrollo.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gesmine11;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class AuditoriaHallazgosNuevos4c3cbb8Test {

    @Autowired private ClienteRepository clientes;
    @Autowired private ExpedienteRepository expedientes;
    @Autowired private CotizacionRepository cotizaciones;
    @Autowired private OrdenDeTrabajoRepository ordenes;
    @Autowired private InstrumentoRepository instrumentos;
    @Autowired private EvaluacionAptitudRepository evaluaciones;
    @Autowired private CalibracionRepository calibraciones;
    @Autowired private InformeTecnicoRepository informes;
    @Autowired private CorrelativoContadorRepository contadores;
    @Autowired private ExpedienteService expedienteService;
    @Autowired private CotizacionService cotizacionService;
    @Autowired private OrdenDeTrabajoService ordenService;
    @Autowired private RevisionTecnicaService revisionService;
    @Autowired private ServicioService servicioService;
    @Autowired private InformeFirmadoService informeFirmadoService;
    @Autowired private TransactionTemplate tx;

    private static final AtomicInteger SECUENCIA_RUC = new AtomicInteger(6000);

    // =====================================================================
    // H9: fallo REAL de servidor en el flujo del certificado. La lógica de
    // negocio es íntegra; el único defecto es de infraestructura (el
    // directorio de almacenamiento deja de ser un directorio). Pregunta del
    // informe: ¿qué excepción concreta cae en qué rama del handler?
    // =====================================================================
    @Test
    void h9_falloRealDeAlmacenamientoDelCertificadoSeRespondeComo500() throws Exception {
        RevisionTecnicaDTO revision = revisionService.crear(revisionNueva(prepararCalibracion()));
        revisionService.registrarResultado(revision.id(), ResultadoRevision.CONFORME, null);
        Long informeId = informes.findAll().stream()
                .filter(i -> i.getRevisionTecnica().getId().equals(revision.id()))
                .findFirst().orElseThrow(() -> new AssertionError("no se generó el informe")).getId();

        // PDF real (generado con PDFBox) para que el fallo provocado sea SOLO el de disco
        // y no la validación de contenido del archivo.
        byte[] pdfBytes;
        try (var doc = new org.apache.pdfbox.pdmodel.PDDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            doc.save(out);
            pdfBytes = out.toByteArray();
        }
        MockMultipartFile pdf = new MockMultipartFile("archivo", "certificado.pdf", "application/pdf", pdfBytes);

        // Fallo real de almacenamiento: el directorio destino pasa a ser un archivo regular.
        Path dir = Path.of("./data/pdf-firmados").toAbsolutePath().normalize();
        boolean eraDirectorio = Files.isDirectory(dir);
        Path respaldo = Files.createTempDirectory("gesmin-pdf-backup-e11");
        if (eraDirectorio) {
            try (Stream<Path> contenido = Files.list(dir)) {
                for (Path p : contenido.toList()) {
                    Files.move(p, respaldo.resolve(p.getFileName().toString()));
                }
            }
            Files.delete(dir);
        }
        Files.write(dir, new byte[0]);

        try {
            // Sonda 1 (directa): qué excepción concreta produce el fallo real.
            IllegalStateException excepcion = assertThrows(IllegalStateException.class,
                    () -> informeFirmadoService.cargar(informeId, pdf));
            System.out.println("### H9 sonda directa: " + excepcion.getClass().getName()
                    + ": " + excepcion.getMessage()
                    + " [causa: " + excepcion.getCause() + "]");

            // Sonda 2 (HTTP): qué responde el handler por ese mismo fallo.
            MockMvc http = MockMvcBuilders
                    .standaloneSetup(new DocumentoPdfController(null, informeFirmadoService))
                    .setControllerAdvice(new com.kevin.backend.exception.GlobalExceptionHandler()).build();
            var respuesta = http.perform(multipart("/api/informes-tecnicos/{id}/pdf-firmado", informeId)
                            .file(pdf)).andReturn().getResponse();
            System.out.println("### H9 HTTP POST /api/informes-tecnicos/" + informeId
                    + "/pdf-firmado (fallo real de disco)");
            System.out.println("### H9 HTTP " + respuesta.getStatus() + " " + respuesta.getContentAsString());
            System.out.println("### H9 clasificacion: fallo de infraestructura del certificado respondido como "
                    + (respuesta.getStatus() >= 500 ? "5xx" : respuesta.getStatus() >= 400 ? "4xx (error del cliente)" : "2xx"));
            assertEquals(500, respuesta.getStatus());
            assertEquals("{\"mensaje\":\"Error interno del servidor.\"}", respuesta.getContentAsString());
        } finally {
            Files.deleteIfExists(dir);
            if (eraDirectorio) {
                Files.createDirectories(dir);
                try (Stream<Path> respaldados = Files.list(respaldo)) {
                    for (Path p : respaldados.toList()) {
                        Files.move(p, dir.resolve(p.getFileName().toString()));
                    }
                }
            }
        }
    }

    // =====================================================================
    // H12: el contador recreado se alimenta de COUNT() y no del máximo vivo.
    // Se simula la limpieza manual del año en curso descrita en el informe:
    // se borra una fila INTERNA (no la última) y el contador; la fila viva
    // restante conserva un número mayor que el nuevo COUNT().
    // =====================================================================
    @Test
    void h12_contadorRecreadoSeAlimentaDeCountYColisionaConVivos() {
        Cliente cliente = nuevoCliente();
        ExpedienteDTO e1 = expedienteService.crear(cliente.getId());
        ExpedienteDTO e2 = expedienteService.crear(cliente.getId());
        ExpedienteDTO e3 = expedienteService.crear(cliente.getId());
        String prefijoAnual = e1.numero().substring(0, 3); // "E" + yy
        long countConLasTres = expedientes.countByNumeroStartingWith(prefijoAnual);

        // Limpieza manual: fila interna + contador, como en un reseed de prueba.
        tx.executeWithoutResult(s -> {
            expedientes.deleteById(e2.id());
            contadores.deleteById(prefijoAnual);
        });

        RuntimeException fallo = null;
        String numeroAsignado = null;
        try {
            numeroAsignado = expedienteService.crear(cliente.getId()).numero();
        } catch (RuntimeException ex) {
            fallo = ex;
        }

        System.out.println("### H12 vivos=" + e1.numero() + "," + e3.numero()
                + " (COUNT previo con las tres=" + countConLasTres + ")");
        System.out.println("### H12 fila interna " + e2.numero() + " y contador " + prefijoAnual
                + " borrados; contador recreado desde COUNT()");
        if (fallo != null) {
            Throwable raiz = fallo;
            while (raiz.getCause() != null) raiz = raiz.getCause();
            System.out.println("### H12 nueva creación FALLO: " + fallo.getClass().getSimpleName()
                    + ": " + fallo.getMessage() + " [raíz: " + raiz + "]");
            System.out.println("### H12 veredicto: el contador recreado NO se resincroniza con el máximo vivo; la creación falla por colisión");
        } else {
            System.out.println("### H12 nueva creación asignó " + numeroAsignado);
            System.out.println("### H12 veredicto: número reasignado sin resincronización");
        }
        assertTrue(fallo != null || numeroAsignado != null, "H12: sin efecto observable");
        if (fallo != null) {
            String texto = String.valueOf(fallo);
            assertTrue(texto.contains("3 intentos") || texto.contains("Unique index") || texto.contains("23505"),
                    "H12: el fallo no es la colisión de correlativo esperada: " + fallo);
        } else {
            assertEquals(e3.numero(), numeroAsignado, "H12: número reasignado inesperado");
        }
    }

    // =====================================================================
    // H15: una OT viva impide degradar su cotización APROBADA.
    // =====================================================================
    @Test
    void h15_cotizacionAprobadaConOtVivaNoPuedeDegradarseARechazada() {
        Cliente cliente = nuevoCliente();
        ExpedienteDTO expediente = expedienteService.crear(cliente.getId());
        ServicioDTO servicio = servicioService.crear(new ServicioDTO(null,
                "AUD-" + UUID.randomUUID().toString().substring(0, 8), "Calibración auditoría E11",
                TipoServicio.CALIBRACION, "Multímetro", null, new BigDecimal("100")));
        CotizacionDTO coi = cotizacionService.crear(new CotizacionDTO(null, null, cliente.getId(),
                null, null, null, expediente.id(), null, LocalDate.now(), null, null, null, null,
                List.of(new DetalleCotizacionDTO(null, null, servicio.id(), null, null, null,
                        BigDecimal.ZERO, 1, null, null, null, null, null, null))));
        cotizacionService.actualizarEstado(coi.id(), EstadoCotizacion.APROBADA);
        OrdenDeTrabajoDTO ot = ordenService.crear(new OrdenDeTrabajoDTO(null, null, coi.id(), null,
                null, null, "Auditor", null, null, LocalDate.now(), null, null,
                List.of(new DetalleOrdenTrabajoDTO(null, "Calibrar multímetro", null, null, null, null))));
        System.out.println("### H15 OT " + ot.numero() + " estado=" + ot.estado()
                + " creada sobre COI " + coi.codigo() + " (guard de APROBADA funcionó al crear)");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> cotizacionService.actualizarEstado(coi.id(), EstadoCotizacion.RECHAZADA));
        EstadoOrdenTrabajo estadoOtViva = tx.execute(s ->
                ordenes.findById(ot.id()).map(OrdenDeTrabajo::getEstado).orElseThrow());
        EstadoCotizacion estadoCotizacion = tx.execute(s -> cotizacionService.obtenerPorId(coi.id()).estado());
        System.out.println("### H15 PATCH COI " + coi.codigo() + " estado=RECHAZADA con OT viva → bloqueado: "
                + error.getMessage());
        System.out.println("### H15 invariante: OT " + ot.id() + " sigue " + estadoOtViva
                + " sobre cotización " + estadoCotizacion);
        assertTrue(error.getMessage().contains("Orden de Trabajo asociada"));
        assertEquals(EstadoCotizacion.APROBADA, estadoCotizacion);
        assertEquals(EstadoOrdenTrabajo.PENDIENTE, estadoOtViva);
    }

    // ----------------------------- helpers -------------------------------
    private RevisionTecnicaDTO revisionNueva(Calibracion calibracion) {
        return new RevisionTecnicaDTO(null, calibracion.getId(), null, LocalDate.now(),
                "Auditor", null, null);
    }

    /** Misma construcción de cadena que AuditoriaH3Cruzada4c3cbb8Test (E9). */
    private Calibracion prepararCalibracion() {
        Cliente cliente = nuevoCliente();
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setCodigo("AUDCOI" + UUID.randomUUID());
        cotizacion.setCliente(cliente);
        cotizacion.setFechaEmision(LocalDate.now());
        cotizacion.setEstado(EstadoCotizacion.APROBADA);
        cotizacion.setMontoTotal(BigDecimal.ZERO);
        cotizacion = cotizaciones.saveAndFlush(cotizacion);

        OrdenDeTrabajo orden = new OrdenDeTrabajo();
        orden.setNumero("AUDOT" + UUID.randomUUID());
        orden.setCotizacion(cotizacion);
        orden.setFecha(LocalDate.now());
        orden = ordenes.saveAndFlush(orden);

        Instrumento instrumento = new Instrumento();
        instrumento.setCliente(cliente);
        instrumento.setTipo("Multímetro");
        instrumento.setMarca("Fluke");
        instrumento.setModelo("87V");
        instrumento.setSerie("AUD-" + UUID.randomUUID());
        instrumento = instrumentos.saveAndFlush(instrumento);

        EvaluacionAptitud evaluacion = new EvaluacionAptitud();
        evaluacion.setOrdenDeTrabajo(orden);
        evaluacion.setInstrumento(instrumento);
        evaluacion.setFechaEvaluacion(LocalDate.now());
        evaluacion.setResultado(ResultadoEvaluacion.APTO);
        evaluacion = evaluaciones.saveAndFlush(evaluacion);

        Calibracion calibracion = new Calibracion();
        calibracion.setEvaluacionAptitud(evaluacion);
        calibracion.setFecha(LocalDate.now());
        calibracion.setEstado(EstadoCalibracion.COMPLETADA);
        return calibraciones.saveAndFlush(calibracion);
    }

    private Cliente nuevoCliente() {
        Cliente cliente = new Cliente();
        cliente.setRazonSocial("Auditoría Hallazgos Nuevos");
        cliente.setRuc("30" + String.format("%09d", SECUENCIA_RUC.getAndIncrement()));
        return clientes.saveAndFlush(cliente);
    }
}
