package com.kevin.backend.service;

import com.kevin.backend.dto.ExpedienteDTO;
import com.kevin.backend.dto.RevisionTecnicaDTO;
import com.kevin.backend.model.*;
import com.kevin.backend.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Auditoría adversarial del commit 23ff2bd (H1, H2, H3).
 * A diferencia de HallazgosConcurrenciaTest (que simula la colisión lanzando la
 * excepción a mano y usa calibraciones distintas sin barrier), aquí se fuerza
 * colisión real sobre el mismo prefijo con barrier y se verifica huecos,
 * duplicados, commits perdidos y atomicidad revisión+informe.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gesminauditoria;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class AuditoriaAdversarial23ff2bdTest {

    @Autowired private ClienteRepository clientes;
    @Autowired private ExpedienteRepository expedientes;
    @Autowired private CotizacionRepository cotizaciones;
    @Autowired private OrdenDeTrabajoRepository ordenes;
    @Autowired private InstrumentoRepository instrumentos;
    @Autowired private EvaluacionAptitudRepository evaluaciones;
    @Autowired private CalibracionRepository calibraciones;
    @Autowired private RevisionTecnicaRepository revisiones;
    @Autowired private InformeTecnicoRepository informes;
    @Autowired private ExpedienteService expedienteService;
    @Autowired private RevisionTecnicaService revisionService;
    @Autowired private CorrelativoRetry correlativos; // el bean REAL del fix

    private static final AtomicInteger SECUENCIA_RUC = new AtomicInteger(5000);

    // =====================================================================
    // H1: repetir CONFORME sobre la misma revisión (serial).
    // =====================================================================
    @Test
    void h1_repetirConformeNoDuplicaInformeNiObservaciones() {
        Calibracion cal = prepararCalibracion();
        RevisionTecnicaDTO rev = revisionService.crear(revision(cal));
        revisionService.registrarResultado(rev.id(), ResultadoRevision.CONFORME, "Obs original");

        RevisionTecnicaDTO repetido = revisionService.registrarResultado(
                rev.id(), ResultadoRevision.CONFORME, "Obs original");

        assertEquals("Obs original", repetido.observaciones(),
                "H1: el PATCH repetido debe conservar las observaciones originales");
        assertEquals(ResultadoRevision.CONFORME, repetido.resultado());
        assertEquals(1L, informes.findAll().stream()
                        .filter(i -> i.getRevisionTecnica().getId().equals(rev.id())).count(),
                "H1: debe existir exactamente un informe para la revisión");
        System.out.println("### H1 serial OK: observaciones='" + repetido.observaciones()
                + "', informes de la revisión=1");
    }

    @Test
    void respuesta_h1_cambioDeObservacionReemiteSinDuplicadoVigente() {
        Calibracion cal = prepararCalibracion();
        RevisionTecnicaDTO rev = revisionService.crear(revision(cal));
        revisionService.registrarResultado(rev.id(), ResultadoRevision.CONFORME, "Texto inicial");
        InformeTecnico anterior = informes.findAll().stream()
                .filter(i -> i.getRevisionTecnica().getId().equals(rev.id()))
                .findFirst().orElseThrow();

        RevisionTecnicaDTO cambiado = revisionService.registrarResultado(
                rev.id(), ResultadoRevision.CONFORME, "Texto corregido");
        List<InformeTecnico> historial = informes.findAll().stream()
                .filter(i -> i.getRevisionTecnica().getId().equals(rev.id())).toList();
        assertEquals("Texto corregido", cambiado.observaciones());
        assertEquals(2, historial.size());
        assertEquals(EstadoInforme.ANULADO, informes.findById(anterior.getId()).orElseThrow().getEstado());
        assertEquals(1L, historial.stream().filter(i -> i.getEstado() != EstadoInforme.ANULADO).count());
        revisionService.registrarResultado(rev.id(), ResultadoRevision.CONFORME, "Texto corregido");
        assertEquals(2L, informes.findAll().stream()
                .filter(i -> i.getRevisionTecnica().getId().equals(rev.id())).count());
        System.out.println("### H1 observación cambiada: anterior=ANULADO, histórico=2, vigente=1; PATCH idéntico no creó tercero");
    }

    // =====================================================================
    // H2: el ciclo NO_CONFORME -> corregir -> nueva revisión -> CONFORME
    // DESPUÉS de que existe un informe de la primera vuelta.
    // =====================================================================
    @Test
    void h2_cicloNoConformeTrasInformeQuedaBloqueado() {
        Calibracion cal = prepararCalibracion();
        RevisionTecnicaDTO r1 = revisionService.crear(revision(cal));
        RevisionTecnicaDTO r2 = revisionService.crear(revision(cal));
        revisionService.registrarResultado(r1.id(), ResultadoRevision.CONFORME, "Primera vuelta");
        InformeTecnico original = informes.findAll().stream()
                .filter(i -> i.getRevisionTecnica().getId().equals(r1.id()))
                .findFirst().orElseThrow();

        assertThrows(IllegalArgumentException.class, () ->
                revisionService.registrarResultado(r2.id(), ResultadoRevision.NO_CONFORME, "Falla ajena"));
        revisionService.registrarResultado(r1.id(), ResultadoRevision.NO_CONFORME, "Falla confirmada");
        assertEquals(EstadoInforme.ANULADO, informes.findById(original.getId()).orElseThrow().getEstado());
        assertEquals(EstadoCalibracion.EN_PROCESO,
                calibraciones.findById(cal.getId()).orElseThrow().getEstado());

        Calibracion corregida = calibraciones.findById(cal.getId()).orElseThrow();
        corregida.setEstado(EstadoCalibracion.COMPLETADA);
        calibraciones.saveAndFlush(corregida);
        revisionService.registrarResultado(r1.id(), ResultadoRevision.CONFORME, "Corregida");
        assertEquals(2L, informes.findAll().stream()
                .filter(i -> i.getRevisionTecnica().getId().equals(r1.id())).count());
        assertEquals(1L, informes.findAll().stream()
                .filter(i -> i.getRevisionTecnica().getId().equals(r1.id())
                        && i.getEstado() != EstadoInforme.ANULADO).count());
        System.out.println("### H2 recertificación: anterior=ANULADO nuevo=GENERADO");
    }

    @Test
    void respuesta_h2_nuevaRevisionTrasAnularInformePrevio() {
        Calibracion cal = prepararCalibracion();
        RevisionTecnicaDTO primera = revisionService.crear(revision(cal));
        revisionService.registrarResultado(primera.id(), ResultadoRevision.CONFORME, "Inicial");
        InformeTecnico anterior = informes.findAll().stream()
                .filter(i -> i.getRevisionTecnica().getId().equals(primera.id()))
                .findFirst().orElseThrow();
        revisionService.registrarResultado(primera.id(), ResultadoRevision.NO_CONFORME, "Corrección");
        Calibracion corregida = calibraciones.findById(cal.getId()).orElseThrow();
        corregida.setEstado(EstadoCalibracion.COMPLETADA);
        calibraciones.saveAndFlush(corregida);

        RevisionTecnicaDTO segunda = revisionService.crear(revision(cal));
        revisionService.registrarResultado(segunda.id(), ResultadoRevision.CONFORME, "Revisión nueva");
        List<InformeTecnico> historial = informes.findAll().stream()
                .filter(i -> i.getRevisionTecnica().getCalibracion().getId().equals(cal.getId())).toList();
        assertEquals(2, historial.size());
        assertEquals(EstadoInforme.ANULADO, informes.findById(anterior.getId()).orElseThrow().getEstado());
        assertEquals(1L, historial.stream().filter(i -> i.getEstado() != EstadoInforme.ANULADO).count());
        System.out.println("### H2 revisión nueva tras ANULADO: anterior=" + anterior.getNumero()
                + ", nueva=" + historial.stream().filter(i -> i.getEstado() != EstadoInforme.ANULADO)
                .findFirst().orElseThrow().getNumero());
    }

    // =====================================================================
    // H3-A1: dos CONFORME simultáneos (barrier) sobre LA MISMA revisión.
    // =====================================================================
    @Test
    @Timeout(90)
    void h3a1_dosConformesSimultaneosMismaRevision() throws Exception {
        Calibracion cal = prepararCalibracion();
        RevisionTecnicaDTO rev = revisionService.crear(revision(cal));

        List<Intento> intentos = enParalelo(2, () ->
                revisionService.registrarResultado(rev.id(), ResultadoRevision.CONFORME, null));

        long informesCreados = informes.findAll().stream()
                .filter(i -> i.getRevisionTecnica().getId().equals(rev.id())).count();
        System.out.println("### H3A1 exitosos=" + intentos.stream().filter(i -> i.error() == null).count()
                + "/2 informes=" + informesCreados);
        intentos.forEach(i -> System.out.println("### H3A1 " + i));

        assertEquals(1L, informesCreados, "H3A1: un solo informe para la misma revisión");
        for (Intento i : intentos) {
            if (i.error() != null) {
                assertTrue(i.error().contains("solo puede registrarse una vez")
                                || i.error().contains("ya tiene un informe"),
                        "H3A1: error inesperado => " + i.error());
            }
        }
    }

    // =====================================================================
    // H3-A2: cuatro CONFORME simultáneos (barrier) sobre cuatro calibraciones
    // DISTINTAS: ventana de colisión REAL de count()+1 del IT (unique numero).
    // Verifica: sin duplicados, sin huecos, sin commits perdidos.
    // =====================================================================
    @Test
    @Timeout(120)
    void h3a2_cuatroConformesSimultaneosColisionRealDeIT() throws Exception {
        String prefijo = prefijoDe("IT");
        long baseline = correlativoMaximo(
                informes.findAll().stream().map(InformeTecnico::getNumero).toList(), prefijo);

        List<RevisionTecnicaDTO> objetivos = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Calibracion cal = prepararCalibracion();
            objetivos.add(revisionService.crear(revision(cal)));
        }

        List<Intento> intentos = enParalelo(4, idx ->
                revisionService.registrarResultado(objetivos.get(idx).id(),
                        ResultadoRevision.CONFORME, null));

        List<Integer> correlativos = new ArrayList<>();
        for (RevisionTecnicaDTO objetivo : objetivos) {
            informes.findAll().stream()
                    .filter(i -> i.getRevisionTecnica().getId().equals(objetivo.id()))
                    .map(i -> Integer.parseInt(i.getNumero().substring(prefijo.length())))
                    .forEach(correlativos::add);
        }
        correlativos.sort(Integer::compareTo);

        System.out.println("### H3A2 exitosos=" + intentos.stream().filter(i -> i.error() == null).count()
                + "/4 correlativos=" + correlativos + " (baseline=" + baseline + ")");
        intentos.forEach(i -> System.out.println("### H3A2 " + i));

        assertEquals(4, intentos.stream().filter(i -> i.error() == null).count(),
                "H3A2: COMMIT PERDIDO — alguna emisión falló (¿MAX_INTENTOS=3 agotado con 4 competidores?)");
        assertEquals(4, new HashSet<>(correlativos).size(),
                "H3A2: DUPLICADO de correlativo IT => H3 refutado");
        assertEquals(List.of((int) baseline + 1, (int) baseline + 2, (int) baseline + 3, (int) baseline + 4),
                correlativos, "H3A2: HUECO en la secuencia de correlativos IT");
    }

    // =====================================================================
    // H3-B: cuatro expedientes simultáneos (barrier), mismo prefijo E+yy y
    // unique(numero): carrera pura de count()+1 con retry REAL del fix.
    // =====================================================================
    @Test
    @Timeout(120)
    void h3b_cuatroExpedientesSimultaneosColisionRealDeE() throws Exception {
        Cliente cliente = nuevoCliente();
        String prefijo = prefijoDe("E");
        long baseline = correlativoMaximo(expedienteNumeros(), prefijo);

        List<Intento> intentos = enParalelo(4, idx -> expedienteService.crear(cliente.getId()));

        List<Integer> nuevos = expedienteNumeros().stream()
                .map(n -> Integer.parseInt(n.substring(prefijo.length())))
                .filter(c -> c > baseline)
                .sorted()
                .collect(Collectors.toList());

        System.out.println("### H3B exitosos=" + intentos.stream().filter(i -> i.error() == null).count()
                + "/4 nuevos=" + nuevos + " (baseline=" + baseline + ")");
        intentos.forEach(i -> System.out.println("### H3B " + i));

        assertEquals(4, intentos.stream().filter(i -> i.error() == null).count(),
                "H3B: COMMIT PERDIDO — alguna creación falló");
        assertEquals(4, nuevos.size(), "H3B: deben existir 4 expedientes nuevos");
        assertEquals(4, new HashSet<>(nuevos).size(), "H3B: DUPLICADO de correlativo E => H3 refutado");
        assertEquals(List.of((int) baseline + 1, (int) baseline + 2, (int) baseline + 3, (int) baseline + 4),
                nuevos, "H3B: HUECO en la secuencia de correlativos E");
    }

    @Test
    @Timeout(120)
    void respuesta_h3_ochoCompetidoresSuperanTresIntentosEnMismoPrefijo() throws Exception {
        final int n = 8; // N > MAX_INTENTOS=3
        Cliente cliente = nuevoCliente();
        String prefijoE = prefijoDe("E");
        long baseE = correlativoMaximo(expedienteNumeros(), prefijoE);
        List<Intento> expedientesConcurrentes = enParalelo(n,
                idx -> expedienteService.crear(cliente.getId()));
        List<Integer> nuevosE = expedienteNumeros().stream().filter(x -> x.startsWith(prefijoE))
                .map(x -> Integer.parseInt(x.substring(prefijoE.length())))
                .filter(x -> x > baseE).sorted().toList();
        System.out.println("### H3 E barrier=8, intentos=3, resultados=" +
                expedientesConcurrentes.stream().map(x -> x.ok() ? "commit" : "error").toList()
                + ", correlativos=" + nuevosE);
        assertTrue(expedientesConcurrentes.stream().allMatch(Intento::ok),
                "Ningún competidor E debe perder su commit: " + expedientesConcurrentes);
        assertEquals(java.util.stream.IntStream.rangeClosed(1, n)
                .map(x -> (int) baseE + x).boxed().toList(), nuevosE);

        String prefijoIT = prefijoDe("IT");
        long baseIT = correlativoMaximo(informes.findAll().stream()
                .map(InformeTecnico::getNumero).toList(), prefijoIT);
        List<RevisionTecnicaDTO> revisionesObjetivo = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            revisionesObjetivo.add(revisionService.crear(revision(prepararCalibracion())));
        }
        List<Intento> informesConcurrentes = enParalelo(n, idx ->
                revisionService.registrarResultado(revisionesObjetivo.get(idx).id(),
                        ResultadoRevision.CONFORME, null));
        List<Integer> nuevosIT = informes.findAll().stream()
                .filter(x -> revisionesObjetivo.stream().anyMatch(r ->
                        r.id().equals(x.getRevisionTecnica().getId())))
                .map(x -> Integer.parseInt(x.getNumero().substring(prefijoIT.length())))
                .sorted().toList();
        System.out.println("### H3 IT barrier=8, intentos=3, resultados=" +
                informesConcurrentes.stream().map(x -> x.ok() ? "commit" : "error").toList()
                + ", correlativos=" + nuevosIT);
        assertTrue(informesConcurrentes.stream().allMatch(Intento::ok),
                "Ningún competidor IT debe perder su commit: " + informesConcurrentes);
        assertEquals(java.util.stream.IntStream.rangeClosed(1, n)
                .map(x -> (int) baseIT + x).boxed().toList(), nuevosIT);
    }

    // =====================================================================
    // H3-C: reintento que ESCAPA de una colisión REAL de constraint
    // (unique ruc), no simulada: intento 1 colisiona de verdad, intento 2
    // usa un RUC libre. Usa el bean CorrelativoRetry real del fix.
    // =====================================================================
    @Test
    @Timeout(60)
    void h3c_reintentoDelFixEscapaDeColisionRealDeConstraint() {
        String rucOcupado = "20" + String.format("%09d", SECUENCIA_RUC.getAndIncrement());
        String rucLibre = "20" + String.format("%09d", SECUENCIA_RUC.getAndIncrement());
        clientes.saveAndFlush(clienteConRuc(rucOcupado));

        Queue<String> rucs = new ConcurrentLinkedQueue<>(List.of(rucOcupado, rucLibre));
        AtomicInteger intentos = new AtomicInteger();

        Long id = correlativos.ejecutar(() -> {
            intentos.incrementAndGet();
            Cliente c = clienteConRuc(rucs.poll());
            return clientes.saveAndFlush(c).getId();
        });

        System.out.println("### H3C intentos=" + intentos.get() + " id=" + id);
        assertEquals(2, intentos.get(),
                "H3C: la colisión REAL de unique(ruc) debe disparar exactamente un reintento");
        assertNotNull(id, "H3C: el segundo intento debe comprometer su fila");
        assertEquals(1, clientes.findAll().stream().filter(c -> rucOcupado.equals(c.getRuc())).count(),
                "H3C: el intento 1 no debe dejar filas (rollback limpio de su transacción)");
        assertEquals(1, clientes.findAll().stream().filter(c -> rucLibre.equals(c.getRuc())).count(),
                "H3C: el intento 2 debe haber comprometido su fila");
    }

    // =====================================================================
    // H3-D: ROMPER el informe a propósito. El bean @Primary deja insertar el
    // IT (saveAndFlush dentro de la tx) y DESPUÉS explota. La revisión NO
    // debe quedar CONFORME huérfana y el IT no debe quedar comprometido.
    // =====================================================================
    @Test
    @Timeout(60)
    void h3d_falloAlPersistirInformeDebeRevertirLaRevision() {
        Calibracion cal = prepararCalibracion();
        RevisionTecnicaDTO rev = revisionService.crear(revision(cal));
        long informesAntes = informes.count();

        InformeTecnicoServiceSaboteado.armado = true;
        try {
            Exception explota = assertThrows(Exception.class, () ->
                    revisionService.registrarResultado(rev.id(), ResultadoRevision.CONFORME, null));
            System.out.println("### H3D explosion => " + explota);

            assertEquals(1, InformeTecnicoServiceSaboteado.explosiones.get(),
                    "H3D: el sabotaje debió ejecutarse exactamente una vez vía el bean primario");

            RevisionTecnica despues = revisiones.findById(rev.id()).orElseThrow();
            System.out.println("### H3D estado revisión tras explosión: " + despues.getResultado()
                    + " | informes antes=" + informesAntes + " después=" + informes.count());

            assertEquals(ResultadoRevision.PENDIENTE, despues.getResultado(),
                    "H3D REFUTADO: la revisión quedó CONFORME huérfana sin informe comprometido");
            assertEquals(informesAntes, informes.count(),
                    "H3D REFUTADO: el informe explotado quedó comprometido en la base");
            assertEquals(EstadoCalibracion.COMPLETADA,
                    calibraciones.findById(cal.getId()).orElseThrow().getEstado(),
                    "H3D: el estado de la calibración no debió cambiar");
        } finally {
            InformeTecnicoServiceSaboteado.armado = false;
        }

        revisionService.registrarResultado(rev.id(), ResultadoRevision.CONFORME, "Tras sabotaje");
        assertEquals(1L, informes.findAll().stream()
                        .filter(i -> i.getRevisionTecnica().getId().equals(rev.id())).count(),
                "H3D: tras desarmar el sabotaje la emisión funciona y hay un solo informe");
    }

    @TestConfiguration
    static class SabotajeConfig {
        @Bean
        @Primary
        InformeTecnicoService informeTecnicoServiceSaboteado(InformeTecnicoRepository repo,
                                                             InformeFirmadoService firmados, CorrelativoService contador) {
            return new InformeTecnicoServiceSaboteado(repo, firmados, contador);
        }
    }

    static class InformeTecnicoServiceSaboteado extends InformeTecnicoService {
        static volatile boolean armado = false;
        static final AtomicInteger explosiones = new AtomicInteger();

        InformeTecnicoServiceSaboteado(InformeTecnicoRepository repo, InformeFirmadoService firmados,
                                       CorrelativoService contador) {
            super(repo, firmados, contador);
        }

        @Override
        @Transactional(propagation = Propagation.MANDATORY)
        public InformeTecnico generarDesdeRevision(RevisionTecnica revision) {
            InformeTecnico informe = super.generarDesdeRevision(revision); // IT ya insertado (flush)
            if (armado) {
                explosiones.incrementAndGet();
                throw new IllegalStateException("SABOTAJE: fallo al persistir el informe");
            }
            return informe;
        }
    }

    // ------------------------------- helpers ----------------------------

    private record Intento(boolean ok, Object resultado, String error) {
        @Override
        public String toString() {
            return ok ? "ok=" + resumen(resultado) : "FALLO=" + error;
        }
        private String resumen(Object r) {
            if (r instanceof RevisionTecnicaDTO dto) return "rev=" + dto.id() + "/" + dto.resultado();
            if (r instanceof ExpedienteDTO e) return "exp=" + e.numero();
            return String.valueOf(r);
        }
    }

    private interface OperacionIdx<T> { T correr(int indice) throws Exception; }
    private interface Operacion<T> { T correr() throws Exception; }

    private List<Intento> enParalelo(int hebras, Operacion<?> op) throws Exception {
        return enParalelo(hebras, idx -> op.correr());
    }

    private List<Intento> enParalelo(int hebras, OperacionIdx<?> op) throws Exception {
        CyclicBarrier barrera = new CyclicBarrier(hebras);
        ExecutorService pool = Executors.newFixedThreadPool(hebras);
        try {
            List<Future<Intento>> futures = new ArrayList<>();
            for (int i = 0; i < hebras; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    barrera.await(25, TimeUnit.SECONDS);
                    try {
                        return new Intento(true, op.correr(idx), null);
                    } catch (Exception e) {
                        Throwable raiz = e;
                        while (raiz.getCause() != null) raiz = raiz.getCause();
                        return new Intento(false, null,
                                e.getClass().getSimpleName() + "/" + e.getMessage()
                                        + " [raíz: " + raiz.getClass().getSimpleName()
                                        + ": " + raiz.getMessage() + "]");
                    }
                }));
            }
            List<Intento> out = new ArrayList<>();
            for (Future<Intento> f : futures) out.add(f.get(100, TimeUnit.SECONDS));
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    private String prefijoDe(String tipo) {
        return tipo + String.format("%02d", LocalDate.now().getYear() % 100)
                + String.format("%02d", LocalDate.now().getMonthValue());
    }

    private long correlativoMaximo(List<String> numeros, String prefijo) {
        return numeros.stream()
                .filter(n -> n.startsWith(prefijo))
                .mapToInt(n -> Integer.parseInt(n.substring(prefijo.length())))
                .max().orElse(0);
    }

    private List<String> expedienteNumeros() {
        return expedientes.findAll().stream().map(Expediente::getNumero).collect(Collectors.toList());
    }

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
        cliente.setRazonSocial("Auditoría");
        cliente.setRuc("20" + String.format("%09d", SECUENCIA_RUC.getAndIncrement()));
        return clientes.saveAndFlush(cliente);
    }

    private Cliente clienteConRuc(String ruc) {
        Cliente cliente = new Cliente();
        cliente.setRazonSocial("Auditoría RUC");
        cliente.setRuc(ruc);
        return cliente;
    }

    private RevisionTecnicaDTO revision(Calibracion calibracion) {
        return new RevisionTecnicaDTO(null, calibracion.getId(), null, LocalDate.now(),
                "Auditor", null, null);
    }
}
