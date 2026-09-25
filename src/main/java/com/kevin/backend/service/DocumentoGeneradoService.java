package com.kevin.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/** Guarda una copia inmutable del PDF al crear cada documento. */
@Service
public class DocumentoGeneradoService {
    private static final Logger log = LoggerFactory.getLogger(DocumentoGeneradoService.class);
    private final DocumentoPdfService generador;
    private final Path directorio;

    public DocumentoGeneradoService(DocumentoPdfService generador,
            @Value("${gesmin.pdf-generados-dir:./data/pdf-generados}") String ruta) {
        this.generador = generador;
        this.directorio = Path.of(ruta).toAbsolutePath().normalize();
    }

    public void guardarCotizacion(Long id) {
        guardar("cotizacion-" + id + ".pdf", () -> generador.cotizacion(id));
    }

    public void guardarOrden(Long id) {
        guardar("orden-trabajo-" + id + ".pdf", () -> generador.orden(id));
    }

    public void guardarInforme(Long id) {
        guardar("informe-tecnico-" + id + "-sin-firma.pdf", () -> generador.informe(id));
    }

    public byte[] leerCotizacion(Long id) {
        return leer("cotizacion-" + id + ".pdf", () -> generador.cotizacion(id));
    }

    public byte[] leerOrden(Long id) {
        return leer("orden-trabajo-" + id + ".pdf", () -> generador.orden(id));
    }

    public byte[] leerInforme(Long id) {
        generador.validarInformeDescargable(id);
        return leer("informe-tecnico-" + id + "-sin-firma.pdf", () -> generador.informe(id));
    }

    private void guardar(String nombre, Supplier<byte[]> generar) {
        Path destino = directorio.resolve(nombre);
        Path temporal = null;
        try {
            Files.createDirectories(directorio);
            if (Files.exists(destino)) return;
            byte[] bytes = generar.get();
            temporal = Files.createTempFile(directorio, nombre + "-", ".tmp");
            Files.write(temporal, bytes);
            try {
                // El enlace se crea de forma atómica y nunca reemplaza un snapshot previo.
                Files.createLink(destino, temporal);
            } catch (FileAlreadyExistsException yaExiste) {
                // Otra petición guardó primero la copia de este mismo documento.
            }
        } catch (IOException | RuntimeException e) {
            // La transacción ya confirmó el documento. El GET puede regenerarlo.
            log.error("No se pudo guardar el PDF generado {}", nombre, e);
        } finally {
            if (temporal != null) {
                try { Files.deleteIfExists(temporal); }
                catch (IOException e) { log.warn("No se pudo limpiar el temporal de {}", nombre, e); }
            }
        }
    }

    private byte[] leer(String nombre, Supplier<byte[]> generar) {
        Path archivo = directorio.resolve(nombre);
        if (Files.isRegularFile(archivo)) {
            try {
                return Files.readAllBytes(archivo);
            } catch (IOException e) {
                log.warn("No se pudo leer el PDF persistido {}; se regenerará", nombre, e);
            }
        }
        return generar.get();
    }
}
