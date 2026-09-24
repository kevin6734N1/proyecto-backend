package com.kevin.backend.service;

import com.kevin.backend.dto.InformeTecnicoDTO;
import com.kevin.backend.model.*;
import com.kevin.backend.repository.CotizacionRepository;
import com.kevin.backend.repository.InformeTecnicoRepository;
import com.kevin.backend.repository.OrdenDeTrabajoRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentoPdfServiceTest {
    @TempDir Path temporal;

    @Test
    void generaTresDocumentosDistintosConDatosDelFlujo() throws Exception {
        CotizacionRepository cotizaciones = mock(CotizacionRepository.class);
        OrdenDeTrabajoRepository ordenes = mock(OrdenDeTrabajoRepository.class);
        InformeTecnicoRepository informes = mock(InformeTecnicoRepository.class);
        Datos datos = datos();
        when(cotizaciones.findById(1L)).thenReturn(Optional.of(datos.cotizacion()));
        when(ordenes.findById(2L)).thenReturn(Optional.of(datos.orden()));
        when(informes.findById(3L)).thenReturn(Optional.of(datos.informe()));

        DocumentoPdfService servicio = new DocumentoPdfService(cotizaciones, ordenes, informes);
        byte[] cotizacion = servicio.cotizacion(1L);
        byte[] orden = servicio.orden(2L);
        byte[] informe = servicio.informe(3L);

        assertPdf(cotizacion, "COI260901", "Cliente de Prueba", "150.00");
        assertPdf(orden, "OT260901", "Cliente de Prueba", "Calibrar multímetro");
        assertPdf(informe, "IT260901", "ABC123", "10.02");
        assertNotEquals(java.util.Arrays.hashCode(cotizacion), java.util.Arrays.hashCode(orden));
        assertNotEquals(java.util.Arrays.hashCode(orden), java.util.Arrays.hashCode(informe));

        String salida = System.getProperty("gesmin.pdf.qa.dir");
        if (salida != null) {
            Path carpeta = Path.of(salida);
            Files.createDirectories(carpeta);
            Files.write(carpeta.resolve("cotizacion-qa.pdf"), cotizacion);
            Files.write(carpeta.resolve("orden-qa.pdf"), orden);
            Files.write(carpeta.resolve("informe-qa.pdf"), informe);
        }
    }

    @Test
    void cotizacionLargaSePaginaSinPerderElFinal() throws Exception {
        CotizacionRepository cotizaciones = mock(CotizacionRepository.class);
        Cotizacion cotizacion = datos().cotizacion();
        cotizacion.setObservaciones("Inicio " + "Detalle de servicio. ".repeat(500) + "Fin del documento.");
        when(cotizaciones.findById(1L)).thenReturn(Optional.of(cotizacion));
        DocumentoPdfService servicio = new DocumentoPdfService(cotizaciones,
                mock(OrdenDeTrabajoRepository.class), mock(InformeTecnicoRepository.class));
        try (PDDocument pdf = Loader.loadPDF(servicio.cotizacion(1L))) {
            assertTrue(pdf.getNumberOfPages() > 1);
            assertTrue(new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ").contains("Fin del documento."));
        }
    }
    @Test
    void firmadoRequierePdfRealYSeDescargaSoloTrasAprobacion() {
        InformeTecnicoRepository informes = mock(InformeTecnicoRepository.class);
        InformeTecnicoService servicioInformes = mock(InformeTecnicoService.class);
        InformeTecnico informe = datos().informe();
        when(informes.findById(3L)).thenReturn(Optional.of(informe));


        InformeFirmadoService firmados = new InformeFirmadoService(informes, temporal.toString());
        MockMultipartFile invalido = new MockMultipartFile("archivo", "falso.pdf", "application/pdf", "texto".getBytes());
        assertThrows(IllegalArgumentException.class, () -> firmados.cargar(3L, invalido));
        assertFalse(Files.exists(temporal.resolve("3.pdf")));

        try (org.apache.pdfbox.pdmodel.PDDocument pdf = new org.apache.pdfbox.pdmodel.PDDocument()) {
            pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            java.io.ByteArrayOutputStream salida = new java.io.ByteArrayOutputStream();
            pdf.save(salida);
            byte[] original = salida.toByteArray();
            MockMultipartFile valido = new MockMultipartFile("archivo", "firmado.pdf", "application/pdf", original);
            firmados.cargar(3L, valido);
            assertEquals(EstadoInforme.PDF_CARGADO, informe.getEstado());
            assertThrows(IllegalArgumentException.class, () -> firmados.descargar(3L));
            informe.setEstado(EstadoInforme.APROBADO);
            assertArrayEquals(original, firmados.descargar(3L));
            assertThrows(IllegalArgumentException.class, () -> firmados.cargar(3L, valido));
        } catch (java.io.IOException e) {
            fail(e);
        }
    }

    @Test
    void informeNoPuedeEnviarseSinFirmaYAprobacion() {
        InformeTecnicoRepository repositorio = mock(InformeTecnicoRepository.class);
        InformeTecnico informe = datos().informe();
        when(repositorio.findById(3L)).thenReturn(Optional.of(informe));
        when(repositorio.save(any(InformeTecnico.class))).thenAnswer(call -> call.getArgument(0));
        InformeFirmadoService firmados = mock(InformeFirmadoService.class);
        when(firmados.existe(3L)).thenReturn(false, true);
        InformeTecnicoService servicio = new InformeTecnicoService(repositorio, firmados);

        assertThrows(IllegalArgumentException.class,
                () -> servicio.actualizarEstado(3L, EstadoInforme.ENVIADO));
        informe.setEstado(EstadoInforme.PDF_CARGADO);
        informe.setPdfCargado(true);
        assertThrows(IllegalArgumentException.class,
                () -> servicio.actualizarEstado(3L, EstadoInforme.APROBADO));
        servicio.actualizarEstado(3L, EstadoInforme.APROBADO);
        assertEquals(EstadoInforme.APROBADO, informe.getEstado());
        servicio.actualizarEstado(3L, EstadoInforme.ENVIADO);
        assertEquals(EstadoInforme.ENVIADO, informe.getEstado());
        assertNotNull(informe.getFechaEnvio());
    }
    private void assertPdf(byte[] bytes, String... textos) throws Exception {
        assertEquals("%PDF-", new String(bytes, 0, 5));
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            assertTrue(pdf.getNumberOfPages() >= 1);
            String contenido = new PDFTextStripper().getText(pdf);
            for (String texto : textos) assertTrue(contenido.contains(texto), texto + " ausente en el PDF");
        }
    }

    private Datos datos() {
        Cliente cliente = new Cliente();
        cliente.setRazonSocial("Cliente de Prueba");
        cliente.setRuc("20100047218");
        cliente.setDireccion("Lima");
        Contacto contacto = new Contacto();
        contacto.setNombre("María Pérez");
        contacto.setEmail("maria@example.com");

        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setId(1L);
        cotizacion.setCodigo("COI260901");
        cotizacion.setFechaEmision(LocalDate.of(2026, 9, 24));
        cotizacion.setCliente(cliente);
        cotizacion.setContacto(contacto);
        cotizacion.setMontoTotal(new BigDecimal("150.00"));
        DetalleCotizacion linea = new DetalleCotizacion();
        linea.setCodigoSnapshot("SV-001");
        linea.setNombreSnapshot("Calibración de multímetro");
        linea.setCantidad(1);
        linea.setPrecioUnitario(new BigDecimal("150.00"));
        linea.setSubtotal(new BigDecimal("150.00"));
        linea.setDescuentoPorcentaje(BigDecimal.ZERO);
        linea.setSerieInstrumento("ABC123");
        cotizacion.setDetalles(List.of(linea));

        OrdenDeTrabajo orden = new OrdenDeTrabajo();
        orden.setId(2L);
        orden.setNumero("OT260901");
        orden.setCotizacion(cotizacion);
        orden.setFecha(LocalDate.of(2026, 9, 24));
        orden.setEjecutor("Kevin");
        orden.setLugar("Laboratorio Gesmin");
        DetalleOrdenTrabajo tarea = new DetalleOrdenTrabajo();
        tarea.setActividad("Calibrar multímetro");
        tarea.setEvaluacionInicial("Equipo operativo");
        orden.setDetalles(List.of(tarea));

        Instrumento instrumento = new Instrumento();
        instrumento.setTipo("Multímetro");
        instrumento.setMarca("Fluke");
        instrumento.setModelo("87V");
        instrumento.setSerie("ABC123");
        EvaluacionAptitud evaluacion = new EvaluacionAptitud();
        evaluacion.setInstrumento(instrumento);
        evaluacion.setOrdenDeTrabajo(orden);
        evaluacion.setObservaciones("Apto para calibración");
        Calibracion calibracion = new Calibracion();
        calibracion.setEvaluacionAptitud(evaluacion);
        calibracion.setTecnico("Kevin");
        calibracion.setProcedimiento("PR-CAL-003");
        PuntoCalibracion punto = new PuntoCalibracion();
        punto.setPuntoDescripcion("10 V");
        punto.setValorPatron(new BigDecimal("10.00"));
        punto.setValorMedido(new BigDecimal("10.02"));
        punto.setError(new BigDecimal("0.02"));
        punto.setUnidad("V");
        punto.setDentroDeTolerancia(true);
        calibracion.setPuntos(List.of(punto));
        RevisionTecnica revision = new RevisionTecnica();
        revision.setId(4L);
        revision.setCalibracion(calibracion);
        revision.setResultado(ResultadoRevision.CONFORME);
        revision.setRevisor("Revisor");
        InformeTecnico informe = new InformeTecnico();
        informe.setId(3L);
        informe.setNumero("IT260901");
        informe.setFechaEmision(LocalDate.of(2026, 9, 24));
        informe.setRevisionTecnica(revision);
        return new Datos(cotizacion, orden, informe);
    }

    private record Datos(Cotizacion cotizacion, OrdenDeTrabajo orden, InformeTecnico informe) {}
}
