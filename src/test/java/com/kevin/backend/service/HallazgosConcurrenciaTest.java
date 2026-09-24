package com.kevin.backend.service;

import com.kevin.backend.dto.*;
import com.kevin.backend.model.*;
import com.kevin.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gesminhallazgos;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class HallazgosConcurrenciaTest {
    @Autowired private ClienteRepository clientes;
    @Autowired private ServicioRepository servicios;
    @Autowired private CotizacionRepository cotizaciones;
    @Autowired private OrdenDeTrabajoRepository ordenes;
    @Autowired private InstrumentoRepository instrumentos;
    @Autowired private EvaluacionAptitudRepository evaluaciones;
    @Autowired private CalibracionRepository calibraciones;
    @Autowired private InformeTecnicoRepository informes;
    @Autowired private ExpedienteService expedienteService;
    @Autowired private CotizacionService cotizacionService;
    @Autowired private OrdenDeTrabajoService ordenService;
    @Autowired private RevisionTecnicaService revisionService;
    @Autowired private CorrelativoRetry correlativos;
    private static final AtomicInteger SECUENCIA_RUC = new AtomicInteger(1);

    @Test
    void h1Yh2ImpidenRepetirRevisionYEmitirSegundoInforme() {
        Calibracion calibracion = prepararCalibracion();
        RevisionTecnicaDTO primera = revisionService.crear(revision(calibracion));
        RevisionTecnicaDTO pendienteAnterior = revisionService.crear(revision(calibracion));
        long antes = informes.count();

        revisionService.registrarResultado(primera.id(), ResultadoRevision.CONFORME, "Conforme");
        assertEquals(antes + 1, informes.count());
        revisionService.registrarResultado(primera.id(), ResultadoRevision.CONFORME, "Conforme");
        assertEquals(antes + 1, informes.count());
        assertTrue(informes.existsByRevisionTecnicaId(primera.id()));

        assertThrows(IllegalArgumentException.class, () -> revisionService.crear(revision(calibracion)));
        assertThrows(IllegalArgumentException.class, () ->
                revisionService.registrarResultado(pendienteAnterior.id(), ResultadoRevision.CONFORME, null));
        assertEquals(antes + 1, informes.count());
    }

    @Test
    void dosPatchConformeConcurrentesSoloGeneranUnInforme() throws Exception {
        Calibracion calibracion = prepararCalibracion();
        RevisionTecnicaDTO revision = revisionService.crear(revision(calibracion));
        long antes = informes.count();
        Dos<RevisionTecnicaDTO> respuestas = dosEnParalelo(() ->
                revisionService.registrarResultado(revision.id(), ResultadoRevision.CONFORME, null));
        assertEquals(ResultadoRevision.CONFORME, respuestas.primero().resultado());
        assertEquals(ResultadoRevision.CONFORME, respuestas.segundo().resultado());
        assertEquals(antes + 1, informes.count());
    }
    @Test
    void noConformePermiteCorregirYCrearNuevaRevision() {
        Calibracion calibracion = prepararCalibracion();
        RevisionTecnicaDTO primera = revisionService.crear(revision(calibracion));
        revisionService.registrarResultado(primera.id(), ResultadoRevision.NO_CONFORME, "Corregir");
        assertEquals(EstadoCalibracion.EN_PROCESO,
                calibraciones.findById(calibracion.getId()).orElseThrow().getEstado());
        Calibracion corregida = calibraciones.findById(calibracion.getId()).orElseThrow();
        corregida.setEstado(EstadoCalibracion.COMPLETADA);
        calibraciones.saveAndFlush(corregida);
        assertNotNull(revisionService.crear(revision(calibracion)).id());
    }

    @Test
    void reintentoAbreNuevaTransaccionDespuesDeRollback() {
        String ruc = nuevoRuc();
        AtomicInteger intentos = new AtomicInteger();
        Long id = correlativos.ejecutar(() -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            Cliente cliente = new Cliente();
            cliente.setRazonSocial("Reintento");
            cliente.setRuc(ruc);
            Long creado = clientes.saveAndFlush(cliente).getId();
            if (intentos.incrementAndGet() == 1) {
                throw new DataIntegrityViolationException("Colisión simulada");
            }
            return creado;
        });
        assertEquals(2, intentos.get());
        assertTrue(clientes.findById(id).isPresent());
        assertEquals(1, clientes.findAll().stream().filter(c -> ruc.equals(c.getRuc())).count());
    }

    @Test
    void h3AsignaCorrelativosDistintosEnLasCuatroRutas() throws Exception {
        Cliente cliente = nuevoCliente();
        Dos<ExpedienteDTO> expedientes = dosEnParalelo(() -> expedienteService.crear(cliente.getId()));
        assertNotEquals(expedientes.primero().numero(), expedientes.segundo().numero());

        Servicio servicio = nuevoServicio();
        Dos<CotizacionDTO> cotizacionesCreadas = dosEnParalelo(() ->
                cotizacionService.crear(cotizacion(cliente, servicio)));
        assertNotEquals(cotizacionesCreadas.primero().codigo(), cotizacionesCreadas.segundo().codigo());

        Cotizacion aprobada = cotizaciones.findById(cotizacionesCreadas.primero().id()).orElseThrow();
        aprobada.setEstado(EstadoCotizacion.APROBADA);
        cotizaciones.saveAndFlush(aprobada);
        Dos<OrdenDeTrabajoDTO> ordenesCreadas = dosEnParalelo(() ->
                ordenService.crear(orden(aprobada.getId())));
        assertNotEquals(ordenesCreadas.primero().numero(), ordenesCreadas.segundo().numero());

        Calibracion cal1 = prepararCalibracion();
        Calibracion cal2 = prepararCalibracion();
        RevisionTecnicaDTO r1 = revisionService.crear(revision(cal1));
        RevisionTecnicaDTO r2 = revisionService.crear(revision(cal2));
        dosEnParaleloIds(() -> revisionService.registrarResultado(r1.id(), ResultadoRevision.CONFORME, null),
                () -> revisionService.registrarResultado(r2.id(), ResultadoRevision.CONFORME, null));
        List<InformeTecnico> generados = informes.findAll().stream()
                .filter(i -> i.getRevisionTecnica().getId().equals(r1.id())
                        || i.getRevisionTecnica().getId().equals(r2.id())).toList();
        assertEquals(2, generados.size());
        assertNotEquals(generados.get(0).getNumero(), generados.get(1).getNumero());
    }

    private Calibracion prepararCalibracion() {
        Cliente cliente = nuevoCliente();
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setCodigo("SEEDCOI" + UUID.randomUUID());
        cotizacion.setCliente(cliente);
        cotizacion.setFechaEmision(LocalDate.now());
        cotizacion.setEstado(EstadoCotizacion.APROBADA);
        cotizacion.setMontoTotal(BigDecimal.ZERO);
        cotizacion = cotizaciones.saveAndFlush(cotizacion);

        OrdenDeTrabajo orden = new OrdenDeTrabajo();
        orden.setNumero("SEEDOT" + UUID.randomUUID());
        orden.setCotizacion(cotizacion);
        orden.setFecha(LocalDate.now());
        orden = ordenes.saveAndFlush(orden);

        Instrumento instrumento = new Instrumento();
        instrumento.setCliente(cliente);
        instrumento.setTipo("Multímetro");
        instrumento.setMarca("Fluke");
        instrumento.setModelo("87V");
        instrumento.setSerie("SER-" + UUID.randomUUID());
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
        cliente.setRazonSocial("Cliente de validación");
        cliente.setRuc(nuevoRuc());
        return clientes.saveAndFlush(cliente);
    }

    private String nuevoRuc() {
        return "20" + String.format("%09d", SECUENCIA_RUC.getAndIncrement());
    }

    private Servicio nuevoServicio() {
        Servicio servicio = new Servicio();
        servicio.setCodigo("S-" + UUID.randomUUID());
        servicio.setNombre("Calibración");
        servicio.setTipoServicio(TipoServicio.CALIBRACION);
        servicio.setTipoEquipoAplicable("Multímetro");
        servicio.setPrecioVenta(new BigDecimal("100.00"));
        return servicios.saveAndFlush(servicio);
    }

    private RevisionTecnicaDTO revision(Calibracion calibracion) {
        return new RevisionTecnicaDTO(null, calibracion.getId(), null, LocalDate.now(), "Revisor", null, null);
    }

    private CotizacionDTO cotizacion(Cliente cliente, Servicio servicio) {
        DetalleCotizacionDTO detalle = new DetalleCotizacionDTO(null, null, servicio.getId(), null, null,
                null, BigDecimal.ZERO, 1, null, BigDecimal.ZERO, null, null, null, null);
        return new CotizacionDTO(null, null, cliente.getId(), null, null, null, null, null,
                LocalDate.now(), null, null, null, null, List.of(detalle));
    }

    private OrdenDeTrabajoDTO orden(Long cotizacionId) {
        DetalleOrdenTrabajoDTO detalle = new DetalleOrdenTrabajoDTO(null, "Calibrar", null, null, null, false);
        return new OrdenDeTrabajoDTO(null, null, cotizacionId, null, null, null, "Técnico",
                null, "Laboratorio", LocalDate.now(), null, null, List.of(detalle));
    }

    private <T> Dos<T> dosEnParalelo(Supplier<T> operacion) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        try {
            Future<T> a = pool.submit(() -> { inicio.await(); return operacion.get(); });
            Future<T> b = pool.submit(() -> { inicio.await(); return operacion.get(); });
            inicio.countDown();
            T primero = a.get(15, TimeUnit.SECONDS);
            T segundo = b.get(15, TimeUnit.SECONDS);
            return new Dos<>(primero, segundo);
        } finally {
            pool.shutdownNow();
        }
    }

    private record Dos<T>(T primero, T segundo) {}

    private void dosEnParaleloIds(Supplier<?> a, Supplier<?> b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        try {
            Future<?> primero = pool.submit(() -> { inicio.await(); return a.get(); });
            Future<?> segundo = pool.submit(() -> { inicio.await(); return b.get(); });
            inicio.countDown();
            primero.get(15, TimeUnit.SECONDS);
            segundo.get(15, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }
}
