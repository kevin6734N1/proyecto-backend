package com.kevin.backend.service;

import com.kevin.backend.dto.MarcaDTO;
import com.kevin.backend.model.Marca;
import com.kevin.backend.repository.MarcaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MarcaService {

    private final MarcaRepository marcaRepository;

    public MarcaService(MarcaRepository marcaRepository) {
        this.marcaRepository = marcaRepository;
    }

    public List<MarcaDTO> listar() {
        return marcaRepository.findAll().stream().map(this::toDTO).toList();
    }

    public MarcaDTO crear(MarcaDTO dto) {
        if (marcaRepository.existsByNombreIgnoreCase(dto.nombre())) {
            throw new RuntimeException("Ya existe una marca con el nombre " + dto.nombre());
        }
        Marca marca = new Marca();
        marca.setNombre(dto.nombre());
        return toDTO(marcaRepository.save(marca));
    }

    public void eliminar(Long id) {
        Marca marca = marcaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Marca no encontrada con id " + id));
        marcaRepository.delete(marca);
    }

    private MarcaDTO toDTO(Marca m) {
        return new MarcaDTO(m.getId(), m.getNombre());
    }
}
