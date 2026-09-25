package com.kevin.backend.service;

import com.kevin.backend.dto.ExpedienteDTO;
import com.kevin.backend.dto.RevisionTecnicaDTO;
import com.kevin.backend.model.*;
import com.kevin.backend.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Auditoría H3 (cruzada 23ff2bd vs 4c3cbb8): contención REAL sobre el MISMO
 * prefijo anual, con CyclicBarrier, N > MAX_INTENTOS=3, E e IT por separado.
 * La sonda E/4 no tiene asserts para poder ejecutarla sobre 23ff2bd y medir
 * su comportamiento sin que JUnit corte la evidencia.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gesminh3cruzada;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class AuditoriaH3Cruzada4c3cbb8Test {

    @Autowired private ClienteRepository clientes;
    @Autowired private ExpedienteRepository expedientes;
    @Autowired private CotizacionRepository cotizaciones;
    @Autowired private OrdenDeTrabajoRepository ordenes;
    @Autowired private InstrumentoRepository instrumentos;
    @Autowired private EvaluacionAptitudRepository evaluaciones;
    @Autowired private CalibracionRepository calibraciones;
    @Autowired private InformeTecnicoRepository informes;
    @Autowired private ExpedienteService expedienteService;
    @Autowired private RevisionTecnicaService revisionService;

    private static final AtomicInteger SECUENCIA_RUC = new AtomicInteger(9000);

    // =====================================================================
    // SONDA (sin asserts): 4 expedientes simultáneos, mismo prefijo E.
    // Corre en ambos commits y muestra el comportamiento crudo.
    // =====================================================================
    @Test
    @Timeout(120)
    void cruzado_h3b_cuatroExpedientesMismoPrefijo() throws Exception {
        Cliente cliente = nuevoCliente();
        String prefijo = prefijoDe("E");
        long baseline = correlativoMaximo(expedienteNumeros(), prefijo);

        List<Intento> intentos = enParalelo(4, idx -> expedienteService.crear(cliente.getId()));

        List<Integer> nuevos = expedienteNumeros().stream()
                .map(n -> Integer.parseInt(n.substring(prefijo.length())))
                .filter(c -> c > baseline)
                .sorted()
                .collect(Collectors.toList());

        System.out.println("### CRUZADA H3B exitosos=" + intentos.stream().filter(i -> i.error() == null).count()
                + "/4 nuevos=" + nuevos + " (baseline=" + baseline + ")");
        intentos.forEach(i -> System.out.println("### CRUZADA H3B " + i));
    }

    // =====================================================================
    // CONTENCIÓN REAL E: N=8 > MAX_INTENTOS=3, mismo prefijo, barrier.
    // =====================================================================
    @Test
    @Timeout(120)
    void cruzado_h3_E_N8_superaPresupuestoDeIntentos() throws Exception {
        int n = 8;
        emitirYVerificarE(n);
    }

    // =====================================================================
    // CONTENCIÓN REAL IT: N=8 > MAX_INTENTOS=3, mismo prefijo, barrier.
    // Clave distinta de E: se mide por separado (Regla 5).
    // =====================================================================
    @Test
    @Timeout(120)
    void cruzado_h3_IT_N8_superaPresupuestoDeIntentos() throws Exception {
        int n = 8;
        emitirYVerificarIT(n);
    }

    // =====================================================================
    // LÍMITE: N=24, ocho veces el presupuesto de intentos (3).
    // =====================================================================
    @Test
    @Timeout(180)
    void cruzado_h3_E_N24_ochoVecesElPresupuesto() throws Exception {
        emitirYVerificarE(24);
    }

    @Test
    @Timeout(180)
    void cruzado_h3_IT_N24_ochoVecesElPresupuesto() throws Exception {
        emitirYVerificarIT(24);
    }

    // ----------------------------- núcleo E ------------------------------
    private void emitirYVerificarE(int n) throws Exception {
        Cliente cliente = nuevoCliente();
        String prefijo = prefijoDe("E");
        long base = correlativoMaximo(expedienteNumeros(), prefijo);

        List<Intento> intentos = enParalelo(n, idx -> expedienteService.crear(cliente.getId()));

        List<Integer> nuevos = expedienteNumeros().stream()
                .filter(x -> x.startsWith(prefijo))
                .map(x -> Integer.parseInt(x.substring(prefijo.length())))
                .filter(x -> x > base)
                .sorted()
                .collect(Collectors.toList());

        System.out.println("### CRUZADA H3 E barrier=" + n + ", intentos=3, resultados="
                + intentos.stream().map(x -> x.ok() ? "commit" : "error").toList()
                + ", correlativos=" + nuevos);
        intentos.stream().filter(i -> i.error() != null).limit(3)
                .forEach(i -> System.out.println("### CRUZADA H3 E primerFallo=" + i));

        assertTrue(intentos.stream().allMatch(Intento::ok),
                "COMMIT PERDIDO en E con N=" + n + ": " + intentos);
        assertEquals(n, nuevos.size(), "Cantidad de expedientes nuevos incorrecta");
        assertEquals(new HashSet<>(nuevos).size(), nuevos.size(), "DUPLICADO de correlativo E");
        assertEquals(java.util.stream.IntStream.rangeClosed(1, n)
                .map(x -> (int) base + x).boxed().toList(), nuevos, "HUECO en la secuencia E");
    }

    // ----------------------------- núcleo IT -----------------------------
    private void emitirYVerificarIT(int n) throws Exception {
        String prefijo = prefijoDe("IT");
        long base = correlativoMaximo(
                informes.findAll().stream().map(InformeTecnico::getNumero).toList(), prefijo);

        List<RevisionTecnicaDTO> objetivos = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            objetivos.add(revisionService.crear(revision(prepararCalibracion())));
        }

        List<Intento> intentos = enParalelo(n, idx ->
                revisionService.registrarResultado(objetivos.get(idx).id(),
                        ResultadoRevision.CONFORME, null));

        // Sin depender de relaciones lazy: todo IT nuevo con prefijo y número > base
        // de esta corrida salió de estas n emisiones (nada más corre en paralelo).
        List<Integer> nuevos = informes.findAll().stream()
                .map(InformeTecnico::getNumero)
                .filter(Objects::nonNull)
                .filter(x -> x.startsWith(prefijo))
                .map(x -> Integer.parseInt(x.substring(prefijo.length())))
                .filter(x -> x > base)
                .sorted()
                .collect(Collectors.toList());

        System.out.println("### CRUZADA H3 IT barrier=" + n + ", intentos=3, resultados="
                + intentos.stream().map(x -> x.ok() ? "commit" : "error").toList()
                + ", correlativos=" + nuevos);
        intentos.stream().filter(i -> i.error() != null).limit(3)
                .forEach(i -> System.out.println("### CRUZADA H3 IT primerFallo=" + i));

        assertTrue(intentos.stream().allMatch(Intento::ok),
                "COMMIT PERDIDO en IT con N=" + n + ": " + intentos);
        assertEquals(n, nuevos.size(), "Cantidad de informes nuevos incorrecta");
        assertEquals(new HashSet<>(nuevos).size(), nuevos.size(), "DUPLICADO de correlativo IT");
        assertEquals(java.util.stream.IntStream.rangeClosed(1, n)
                .map(x -> (int) base + x).boxed().toList(), nuevos, "HUECO en la secuencia IT");
    }

    // ----------------------------- helpers -------------------------------
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
            for (Future<Intento> f : futures) out.add(f.get(150, TimeUnit.SECONDS));
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
                .filter(Objects::nonNull)
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
        cliente.setRazonSocial("Auditoría Cruzada H3");
        cliente.setRuc("20" + String.format("%09d", SECUENCIA_RUC.getAndIncrement()));
        return clientes.saveAndFlush(cliente);
    }

    private RevisionTecnicaDTO revision(Calibracion calibracion) {
        return new RevisionTecnicaDTO(null, calibracion.getId(), null, LocalDate.now(),
                "Auditor", null, null);
    }
}
