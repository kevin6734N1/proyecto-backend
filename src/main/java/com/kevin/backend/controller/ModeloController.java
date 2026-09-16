package com.kevin.backend.controller;

import com.kevin.backend.dto.ModeloDTO;
import com.kevin.backend.service.ModeloService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/modelos")
public class ModeloController {

    private final ModeloService modeloService;

    public ModeloController(ModeloService modeloService) {
        this.modeloService = modeloService;
    }

    @GetMapping
    public List<ModeloDTO> listar() {
        return modeloService.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ModeloDTO crear(@Valid @RequestBody ModeloDTO dto) {
        return modeloService.crear(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        modeloService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
