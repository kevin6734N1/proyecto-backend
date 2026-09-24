package com.kevin.backend.service;

import com.kevin.backend.dto.InformeTecnicoDTO;
import com.kevin.backend.model.EstadoInforme;
import com.kevin.backend.model.InformeTecnico;
import com.kevin.backend.model.Instrumento;
import com.kevin.backend.repository.InformeTecnicoRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class InformeFirmadoService {
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private final InformeTecnicoRepository informes;
    private final Path directorio;

    public InformeFirmadoService(InformeTecnicoRepository informes,
                                @Value("${gesmin.pdf-firmados-dir:./data/pdf-firmados}") String ruta) {
        this.informes = informes;
        this.directorio = Path.of(ruta).toAbsolutePath().normalize();
    }

    @Transactional
    public InformeTecnicoDTO cargar(Long id, MultipartFile archivo) {
        InformeTecnico informe = buscar(id);
        if (informe.getEstado() != EstadoInforme.GENERADO || Boolean.TRUE.equals(informe.getPdfCargado())) {
            throw new IllegalArgumentException("El PDF firmado solo puede cargarse una vez cuando el informe está GENERADO.");
        }
        if (archivo == null || archivo.isEmpty() || archivo.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("El PDF firmado debe tener entre 1 byte y 10 MB.");
        }
        byte[] datos;
        try {
            datos = archivo.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el PDF firmado.", e);
        }
        if (datos.length < 5 || datos[0] != '%' || datos[1] != 'P' || datos[2] != 'D'
                || datos[3] != 'F' || datos[4] != '-') {
            throw new IllegalArgumentException("El archivo no es un PDF válido.");
        }
        try (PDDocument pdf = Loader.loadPDF(datos)) {
            if (pdf.getNumberOfPages() == 0) throw new IllegalArgumentException("El PDF no contiene páginas.");
        } catch (IOException e) {
            throw new IllegalArgumentException("El archivo no es un PDF válido.", e);
        }

        Path destino = ruta(id);
        Path temporal = directorio.resolve(id + "-" + UUID.randomUUID() + ".tmp");
        boolean movido = false;
        try {
            Files.createDirectories(directorio);
            Files.write(temporal, datos);
            Files.move(temporal, destino); // falla si ya existe: nunca sobrescribe un firmado
            movido = true;
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int estado) {
                        if (estado != STATUS_COMMITTED) {
                            try { Files.deleteIfExists(destino); } catch (IOException ignored) {}
                        }
                    }
                });
            }
            informe.setPdfCargado(true);
            informe.setFechaCargaPdf(LocalDate.now());
            informe.setEstado(EstadoInforme.PDF_CARGADO);
            informes.saveAndFlush(informe);
            return toDTO(informe);
        } catch (IOException | RuntimeException e) {
            try { Files.deleteIfExists(temporal); } catch (IOException ignored) {}
            if (movido) {
                try { Files.deleteIfExists(destino); } catch (IOException ignored) {}
            }
            if (e instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("No se pudo almacenar el PDF firmado.", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] descargar(Long id) {
        InformeTecnico informe = buscar(id);
        if (!Boolean.TRUE.equals(informe.getPdfCargado())) {
            throw new IllegalArgumentException("Este informe todavía no tiene PDF firmado.");
        }
        if (informe.getEstado() == EstadoInforme.ANULADO) {
            throw new IllegalArgumentException("El informe fue anulado y su PDF firmado no está disponible.");
        }
        if (informe.getEstado() != EstadoInforme.APROBADO && informe.getEstado() != EstadoInforme.ENVIADO) {
            throw new IllegalArgumentException("El PDF firmado estará disponible para Ventas después de la aprobación técnica.");
        }
        try {
            return Files.readAllBytes(ruta(id));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el PDF firmado almacenado.", e);
        }
    }

    public boolean existe(Long id) {
        return Files.isRegularFile(ruta(id));
    }

    private InformeTecnicoDTO toDTO(InformeTecnico informe) {
        Instrumento instrumento = informe.getRevisionTecnica().getCalibracion()
                .getEvaluacionAptitud().getInstrumento();
        return new InformeTecnicoDTO(informe.getId(), informe.getNumero(),
                informe.getRevisionTecnica().getId(),
                instrumento.getMarca() + " " + instrumento.getModelo() + " - Serie " + instrumento.getSerie(),
                informe.getFechaEmision(), informe.getEstado(), informe.getPdfCargado(),
                informe.getFechaCargaPdf(), informe.getFechaEnvio(),
                informe.getFechaAnulacion(), informe.getMotivoAnulacion());
    }

    private InformeTecnico buscar(Long id) {
        return informes.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Informe técnico no encontrado con id " + id));
    }

    private Path ruta(Long id) {
        return directorio.resolve(id + ".pdf");
    }
}
