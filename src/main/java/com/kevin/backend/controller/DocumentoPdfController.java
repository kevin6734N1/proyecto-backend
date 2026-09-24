package com.kevin.backend.controller;

import com.kevin.backend.dto.InformeTecnicoDTO;
import com.kevin.backend.service.DocumentoPdfService;
import com.kevin.backend.service.InformeFirmadoService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
public class DocumentoPdfController {
    private final DocumentoPdfService documentos;
    private final InformeFirmadoService firmados;

    public DocumentoPdfController(DocumentoPdfService documentos, InformeFirmadoService firmados) {
        this.documentos = documentos;
        this.firmados = firmados;
    }

    @GetMapping(value = "/api/cotizaciones/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> cotizacion(@PathVariable Long id) {
        return pdf("cotizacion-" + id + ".pdf", documentos.cotizacion(id));
    }

    @GetMapping(value = "/api/ordenes-trabajo/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> orden(@PathVariable Long id) {
        return pdf("orden-trabajo-" + id + ".pdf", documentos.orden(id));
    }

    @GetMapping(value = "/api/informes-tecnicos/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> informe(@PathVariable Long id) {
        return pdf("informe-tecnico-" + id + "-sin-firma.pdf", documentos.informe(id));
    }

    @PostMapping(value = "/api/informes-tecnicos/{id}/pdf-firmado",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InformeTecnicoDTO cargarFirmado(@PathVariable Long id, @RequestPart("archivo") MultipartFile archivo) {
        return firmados.cargar(id, archivo);
    }

    @GetMapping(value = "/api/informes-tecnicos/{id}/pdf-firmado",
            produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> descargarFirmado(@PathVariable Long id) {
        return pdf("informe-tecnico-" + id + "-firmado.pdf", firmados.descargar(id));
    }

    private static ResponseEntity<byte[]> pdf(String nombre, byte[] contenido) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(nombre, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(contenido.length)
                .body(contenido);
    }
}
