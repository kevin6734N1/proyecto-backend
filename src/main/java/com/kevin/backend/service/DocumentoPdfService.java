package com.kevin.backend.service;

import com.kevin.backend.model.EstadoInforme;
import com.kevin.backend.model.*;
import com.kevin.backend.repository.CotizacionRepository;
import com.kevin.backend.repository.InformeTecnicoRepository;
import com.kevin.backend.repository.OrdenDeTrabajoRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentoPdfService {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final CotizacionRepository cotizaciones;
    private final OrdenDeTrabajoRepository ordenes;
    private final InformeTecnicoRepository informes;

    public DocumentoPdfService(CotizacionRepository cotizaciones, OrdenDeTrabajoRepository ordenes,
                               InformeTecnicoRepository informes) {
        this.cotizaciones = cotizaciones;
        this.ordenes = ordenes;
        this.informes = informes;
    }

    @Transactional(readOnly = true)
    public byte[] cotizacion(Long id) {
        Cotizacion c = cotizaciones.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cotización no encontrada con id " + id));
        try (Lienzo pdf = new Lienzo("COTIZACIÓN - BORRADOR", c.getCodigo())) {
            pdf.dato("Fecha de emisión", fecha(c.getFechaEmision()));
            pdf.dato("Expediente", c.getExpediente() == null ? "Sin expediente" : c.getExpediente().getNumero());
            pdf.seccion("CLIENTE");
            pdf.dato("Empresa", c.getCliente().getRazonSocial());
            pdf.dato("RUC", c.getCliente().getRuc());
            pdf.dato("Dirección", c.getCliente().getDireccion());
            if (c.getContacto() != null) {
                pdf.dato("Atención", c.getContacto().getNombre());
                pdf.dato("Correo", c.getContacto().getEmail());
                pdf.dato("Teléfono", c.getContacto().getTelefono());
            }
            pdf.seccion("PRODUCTOS Y SERVICIOS");
            int item = 1;
            for (DetalleCotizacion d : c.getDetalles()) {
                pdf.dato("Ítem " + item++, d.getCodigoSnapshot() + " - " + d.getNombreSnapshot());
                pdf.dato("Cantidad / precio unitario / descuento", d.getCantidad() + " / "
                        + importe(d.getPrecioUnitario()) + " / " + importe(d.getDescuentoPorcentaje()) + "%");
                if (d.getCostoAdicional() != null && d.getCostoAdicional().signum() != 0) {
                    pdf.dato("Costo adicional", importe(d.getCostoAdicional()));
                }
                pdf.dato("Subtotal de línea", importe(d.getSubtotal()));
                if (d.getComentarios() != null) pdf.parrafo(d.getComentarios());
                if (d.getSerieInstrumento() != null) pdf.dato("Instrumento / serie",
                        valor(d.getMarcaInstrumento()) + " " + valor(d.getModeloInstrumento())
                                + " / " + d.getSerieInstrumento());
                pdf.espacio(7);
            }
            pdf.seccion("RESUMEN");
            pdf.dato("Monto registrado", importe(c.getMontoTotal()));
            pdf.parrafo("Documento preliminar: moneda, IGV y forma de pago pendientes de confirmación.");
            if (c.getFechaVencimiento() != null) pdf.dato("Vigencia hasta", fecha(c.getFechaVencimiento()));
            if (c.getObservaciones() != null) {
                pdf.seccion("OBSERVACIONES");
                pdf.parrafo(c.getObservaciones());
            }
            return pdf.bytes();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar la cotización PDF", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] orden(Long id) {
        OrdenDeTrabajo o = ordenes.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Orden de trabajo no encontrada con id " + id));
        Cotizacion c = o.getCotizacion();
        try (Lienzo pdf = new Lienzo("ORDEN DE TRABAJO", o.getNumero())) {
            pdf.dato("Fecha de emisión", fecha(o.getFecha()));
            pdf.dato("Expediente", o.getExpediente() == null ? "Sin expediente" : o.getExpediente().getNumero());
            pdf.dato("Cotización", c.getCodigo());
            pdf.seccion("CLIENTE");
            pdf.dato("Empresa", c.getCliente().getRazonSocial());
            pdf.dato("Dirección", c.getCliente().getDireccion());
            if (c.getContacto() != null) {
                pdf.dato("Atención", c.getContacto().getNombre());
                pdf.dato("Correo", c.getContacto().getEmail());
                pdf.dato("Teléfono", c.getContacto().getTelefono());
            }
            pdf.seccion("EJECUCIÓN");
            pdf.dato("Ejecutor", o.getEjecutor());
            pdf.dato("Área", o.getArea());
            pdf.dato("Lugar", o.getLugar());
            pdf.dato("Fecha y hora", fecha(o.getFecha()) + (o.getHora() == null ? "" : "  " + o.getHora()));
            pdf.seccion("ACTIVIDADES");
            int item = 1;
            for (DetalleOrdenTrabajo d : o.getDetalles()) {
                pdf.dato("Ítem " + item++, d.getActividad());
                pdf.dato("Evaluación inicial", d.getEvaluacionInicial());
                pdf.dato("Conclusiones", d.getConclusiones());
                pdf.dato("Recomendaciones", d.getRecomendaciones());
                pdf.espacio(8);
            }
            pdf.firmas();
            return pdf.bytes();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar la orden de trabajo PDF", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] informe(Long id) {
        InformeTecnico i = buscarInformeDescargable(id);
        RevisionTecnica r = i.getRevisionTecnica();
        Calibracion cal = r.getCalibracion();
        EvaluacionAptitud ev = cal.getEvaluacionAptitud();
        Instrumento instrumento = ev.getInstrumento();
        OrdenDeTrabajo orden = ev.getOrdenDeTrabajo();
        Cotizacion cot = orden.getCotizacion();
        try (Lienzo pdf = new Lienzo("INFORME TÉCNICO - SIN FIRMA", i.getNumero())) {
            pdf.parrafo("Vista generada desde los datos del sistema. El documento firmado se carga por separado.");
            pdf.seccion("1. INFORME, CLIENTE Y EQUIPO");
            pdf.dato("N° de informe", i.getNumero());
            pdf.dato("Fecha", fecha(i.getFechaEmision()));
            pdf.dato("Tipo de servicio / procedimiento", cal.getProcedimiento());
            pdf.dato("Lugar del servicio", orden.getLugar());
            pdf.dato("Empresa", cot.getCliente().getRazonSocial());
            pdf.dato("Dirección", cot.getCliente().getDireccion());
            if (cot.getContacto() != null) pdf.dato("Atención", cot.getContacto().getNombre());
            pdf.dato("Descripción del equipo", instrumento.getTipo());
            pdf.dato("Marca / modelo", instrumento.getMarca() + " / " + instrumento.getModelo());
            pdf.dato("Serie / código cliente", instrumento.getSerie() + " / " + valor(instrumento.getCodigoCliente()));
            pdf.seccion("2. EVALUACIÓN PRELIMINAR");
            pdf.parrafo(valor(ev.getObservaciones()));
            pdf.seccion("3. TRABAJOS REALIZADOS");
            pdf.dato("Técnico", cal.getTecnico());
            pdf.dato("Procedimiento", cal.getProcedimiento());
            for (DetalleOrdenTrabajo d : orden.getDetalles()) pdf.parrafo(d.getActividad());
            pdf.seccion("4. PRUEBAS Y MEDICIONES");
            if (cal.getPuntos().isEmpty()) pdf.parrafo("No se registraron puntos de calibración.");
            for (PuntoCalibracion p : cal.getPuntos()) {
                pdf.dato(valor(p.getPuntoDescripcion()),
                        "Patrón " + importe(p.getValorPatron()) + " | Medido " + importe(p.getValorMedido())
                                + " | Error " + importe(p.getError()) + " " + valor(p.getUnidad())
                                + " | Tolerancia: " + (Boolean.TRUE.equals(p.getDentroDeTolerancia()) ? "dentro" : "no confirmada/fuera"));
            }
            pdf.seccion("5. CONCLUSIONES");
            pdf.dato("Resultado de revisión", r.getResultado().name());
            pdf.parrafo(valor(r.getObservaciones()));
            pdf.seccion("6. RECOMENDACIONES Y OBSERVACIONES");
            for (DetalleOrdenTrabajo d : orden.getDetalles()) {
                if (d.getRecomendaciones() != null) pdf.parrafo(d.getRecomendaciones());
            }
            pdf.dato("Revisor", r.getRevisor());
            return pdf.bytes();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el informe técnico PDF", e);
        }
    }

    public void validarCotizacionExistente(Long id) {
        cotizaciones.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cotización no encontrada con id " + id));
    }

    public void validarOrdenExistente(Long id) {
        ordenes.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Orden de trabajo no encontrada con id " + id));
    }

    public void validarInformeDescargable(Long id) {
        buscarInformeDescargable(id);
    }

    private InformeTecnico buscarInformeDescargable(Long id) {
        InformeTecnico informe = informes.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Informe técnico no encontrado con id " + id));
        if (informe.getEstado() == EstadoInforme.ANULADO) {
            throw new IllegalArgumentException("El informe fue anulado y no está disponible para descarga.");
        }
        return informe;
    }

    private static String fecha(LocalDate fecha) {
        return fecha == null ? "No registrado" : FECHA.format(fecha);
    }

    private static String importe(BigDecimal numero) {
        return numero == null ? "0.00" : numero.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String valor(String texto) {
        return texto == null || texto.isBlank() ? "No registrado" : texto;
    }

    private static final class Lienzo implements AutoCloseable {
        private static final float MARGEN = 48;
        private static final float ANCHO = PDRectangle.A4.getWidth();
        private static final float ALTO = PDRectangle.A4.getHeight();
        private static final PDType1Font NORMAL = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private static final PDType1Font NEGRITA = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final PDDocument documento = new PDDocument();
        private final String titulo;
        private final String numero;
        private PDPageContentStream contenido;
        private float y;

        Lienzo(String titulo, String numero) throws IOException {
            this.titulo = titulo;
            this.numero = numero;
            pagina();
        }

        void pagina() throws IOException {
            if (contenido != null) contenido.close();
            PDPage p = new PDPage(PDRectangle.A4);
            documento.addPage(p);
            contenido = new PDPageContentStream(documento, p);
            y = ALTO - MARGEN;
            contenido.setNonStrokingColor(new Color(218, 139, 35));
            texto("gesmin", 25, NEGRITA, MARGEN, y);
            contenido.setNonStrokingColor(Color.DARK_GRAY);
            texto(titulo, 13, NEGRITA, MARGEN + 145, y + 4);
            y -= 23;
            texto("SERVICIOS Y SUMINISTROS PARA LABORATORIO", 7, NORMAL, MARGEN, y);
            texto(numero, 10, NEGRITA, ANCHO - MARGEN - ancho(numero, NEGRITA, 10), y);
            y -= 18;
            contenido.setStrokingColor(new Color(218, 139, 35));
            contenido.moveTo(MARGEN, y);
            contenido.lineTo(ANCHO - MARGEN, y);
            contenido.stroke();
            y -= 24;
        }

        void seccion(String titulo) throws IOException {
            reservar(45);
            espacio(8);
            contenido.setNonStrokingColor(new Color(235, 235, 235));
            contenido.addRect(MARGEN, y - 4, ANCHO - 2 * MARGEN, 20);
            contenido.fill();
            contenido.setNonStrokingColor(Color.BLACK);
            texto(titulo, 10, NEGRITA, MARGEN + 6, y + 2);
            y -= 27;
        }

        void dato(String etiqueta, String valor) throws IOException {
            parrafo(etiqueta + ": " + valor(valor));
        }

        void parrafo(String texto) throws IOException {
            for (String linea : valor(texto).split("\\R", -1)) {
                List<String> partes = ajustar(linea, NORMAL, 10, ANCHO - 2 * MARGEN);
                for (String parte : partes) {
                    reservar(17);
                    texto(parte, 10, NORMAL, MARGEN, y);
                    y -= 15;
                }
            }
            y -= 3;
        }

        void espacio(float puntos) throws IOException {
            reservar(puntos + 12);
            y -= puntos;
        }

        void firmas() throws IOException {
            reservar(75);
            y -= 25;
            contenido.setStrokingColor(Color.DARK_GRAY);
            contenido.moveTo(MARGEN + 30, y);
            contenido.lineTo(MARGEN + 210, y);
            contenido.moveTo(MARGEN + 290, y);
            contenido.lineTo(ANCHO - MARGEN - 10, y);
            contenido.stroke();
            y -= 14;
            texto("Firma Gesmin", 9, NORMAL, MARGEN + 75, y);
            texto("Firma del cliente", 9, NORMAL, MARGEN + 325, y);
            y -= 20;
        }
        void reservar(float alto) throws IOException {
            if (y - alto < MARGEN + 24) pagina();
        }

        byte[] bytes() throws IOException {
            if (contenido != null) {
                contenido.close();
                contenido = null;
            }
            int total = documento.getNumberOfPages();
            for (int n = 0; n < total; n++) {
                try (PDPageContentStream pie = new PDPageContentStream(documento, documento.getPage(n),
                        PDPageContentStream.AppendMode.APPEND, true)) {
                    pie.setNonStrokingColor(Color.GRAY);
                    pie.beginText();
                    pie.setFont(NORMAL, 8);
                    pie.newLineAtOffset(MARGEN, 28);
                    pie.showText("Gesmin SRL  |  RUC 20520739280");
                    pie.endText();
                    String pagina = (n + 1) + " de " + total;
                    pie.beginText();
                    pie.setFont(NORMAL, 8);
                    pie.newLineAtOffset(ANCHO - MARGEN - ancho(pagina, NORMAL, 8), 28);
                    pie.showText(pagina);
                    pie.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            documento.save(out);
            return out.toByteArray();
        }

        private void texto(String valor, int tamano, PDType1Font fuente, float x, float y) throws IOException {
            contenido.beginText();
            contenido.setFont(fuente, tamano);
            contenido.newLineAtOffset(x, y);
            contenido.showText(limpiar(valor));
            contenido.endText();
        }

        private static List<String> ajustar(String texto, PDType1Font fuente, int tamano, float maximo) throws IOException {
            List<String> resultado = new ArrayList<>();
            StringBuilder linea = new StringBuilder();
            for (String palabra : limpiar(texto).split(" ")) {
                if (palabra.isEmpty()) continue;
                String candidato = linea.isEmpty() ? palabra : linea + " " + palabra;
                if (ancho(candidato, fuente, tamano) > maximo && !linea.isEmpty()) {
                    resultado.add(linea.toString());
                    linea = new StringBuilder(palabra);
                } else {
                    linea = new StringBuilder(candidato);
                }
            }
            if (!linea.isEmpty()) resultado.add(linea.toString());
            if (resultado.isEmpty()) resultado.add("");
            return resultado;
        }

        private static float ancho(String texto, PDType1Font fuente, int tamano) throws IOException {
            return fuente.getStringWidth(limpiar(texto)) / 1000 * tamano;
        }

        private static String limpiar(String valor) {
            return valor.replace('\u2013', '-').replace('\u2014', '-')
                    .replace('\u2192', '>').replaceAll("[^\\x20-\\x7E\\u00A0-\\u00FF]", "?");
        }

        @Override
        public void close() throws IOException {
            if (contenido != null) contenido.close();
            documento.close();
        }
    }
}
