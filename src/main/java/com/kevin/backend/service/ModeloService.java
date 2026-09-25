package com.kevin.backend.service;

import com.kevin.backend.dto.ModeloDTO;
import com.kevin.backend.model.Modelo;
import com.kevin.backend.repository.ModeloRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ModeloService {

    private final ModeloRepository modeloRepository;

    public ModeloService(ModeloRepository modeloRepository) {
        this.modeloRepository = modeloRepository;
    }

    public List<ModeloDTO> listar() {
        return modeloRepository.findAll().stream().map(this::toDTO).toList();
    }

    public ModeloDTO crear(ModeloDTO dto) {
        if (modeloRepository.existsByNombreIgnoreCase(dto.nombre())) {
            throw new IllegalArgumentException("Ya existe un modelo con el nombre " + dto.nombre());
        }
        Modelo modelo = new Modelo();
        modelo.setNombre(dto.nombre());
        return toDTO(modeloRepository.save(modelo));
    }

    public void eliminar(Long id) {
        Modelo modelo = modeloRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Modelo no encontrado con id " + id));
        modeloRepository.delete(modelo);
    }

    private ModeloDTO toDTO(Modelo m) {
        return new ModeloDTO(m.getId(), m.getNombre());
    }
}
