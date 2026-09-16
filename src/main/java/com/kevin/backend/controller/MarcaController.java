package com.kevin.backend.controller;

import com.kevin.backend.dto.MarcaDTO;
import com.kevin.backend.service.MarcaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/marcas")
public class MarcaController {

    private final MarcaService marcaService;

    public MarcaController(MarcaService marcaService) {
        this.marcaService = marcaService;
    }

    @GetMapping
    public List<MarcaDTO> listar() {
        return marcaService.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MarcaDTO crear(@Valid @RequestBody MarcaDTO dto) {
        return marcaService.crear(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        marcaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
