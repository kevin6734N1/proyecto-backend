package com.kevin.backend.service;

import com.kevin.backend.dto.HerramientaDTO;
import com.kevin.backend.model.Herramienta;
import com.kevin.backend.repository.HerramientaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HerramientaService {

    private final HerramientaRepository herramientaRepository;

    public HerramientaService(HerramientaRepository herramientaRepository) {
        this.herramientaRepository = herramientaRepository;
    }

    public List<HerramientaDTO> listar(boolean soloActivas) {
        List<Herramienta> herramientas = soloActivas
                ? herramientaRepository.findByActivoTrue()
                : herramientaRepository.findAll();
        return herramientas.stream().map(this::toDTO).toList();
    }

    public List<HerramientaDTO> buscar(String texto) {
        return herramientaRepository.findByDescripcionContainingIgnoreCase(texto)
                .stream().map(this::toDTO).toList();
    }

    public HerramientaDTO obtenerPorId(Long id) {
        return toDTO(buscarEntidadPorId(id));
    }

    public HerramientaDTO crear(HerramientaDTO dto) {
        if (herramientaRepository.existsByCodigoInterno(dto.codigoInterno())) {
            throw new IllegalArgumentException("Ya existe una herramienta con el código interno " + dto.codigoInterno());
        }
        Herramienta herramienta = new Herramienta();
        aplicarDatos(herramienta, dto);
        herramienta.setActivo(true);
        return toDTO(herramientaRepository.save(herramienta));
    }

    public HerramientaDTO actualizar(Long id, HerramientaDTO dto) {
        Herramienta herramienta = buscarEntidadPorId(id);

        if (!herramienta.getCodigoInterno().equals(dto.codigoInterno())
                && herramientaRepository.existsByCodigoInterno(dto.codigoInterno())) {
            throw new IllegalArgumentException("Ya existe una herramienta con el código interno " + dto.codigoInterno());
        }

        aplicarDatos(herramienta, dto);
        return toDTO(herramientaRepository.save(herramienta));
    }

    // Soft-delete: un patrón que ya fue usado en calibraciones no debe desaparecer del historial
    public void desactivar(Long id) {
        Herramienta herramienta = buscarEntidadPorId(id);
        herramienta.setActivo(false);
        herramientaRepository.save(herramienta);
    }

    private void aplicarDatos(Herramienta h, HerramientaDTO dto) {
        h.setDescripcion(dto.descripcion());
        h.setMarca(dto.marca());
        h.setModelo(dto.modelo());
        h.setSerie(dto.serie());
        h.setCodigoInterno(dto.codigoInterno());
    }

    private Herramienta buscarEntidadPorId(Long id) {
        return herramientaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Herramienta no encontrada con id " + id));
    }

    private HerramientaDTO toDTO(Herramienta h) {
        return new HerramientaDTO(
                h.getId(), h.getDescripcion(), h.getMarca(), h.getModelo(),
                h.getSerie(), h.getCodigoInterno(), h.getActivo()
        );
    }
}
