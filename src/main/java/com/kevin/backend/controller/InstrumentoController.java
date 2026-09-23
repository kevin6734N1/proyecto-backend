package com.kevin.backend.controller;

import com.kevin.backend.dto.InstrumentoDTO;
import com.kevin.backend.model.UbicacionInstrumento;
import com.kevin.backend.service.InstrumentoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/instrumentos")
public class InstrumentoController {

    private final InstrumentoService instrumentoService;

    public InstrumentoController(InstrumentoService instrumentoService) {
        this.instrumentoService = instrumentoService;
    }

    @GetMapping
    public List<InstrumentoDTO> listar(@RequestParam(required = false) UbicacionInstrumento ubicacion,
                                        @RequestParam(required = false) Long clienteId) {
        if (ubicacion != null) return instrumentoService.listarPorUbicacion(ubicacion);
        if (clienteId != null) return instrumentoService.listarPorCliente(clienteId);
        return instrumentoService.listar();
    }

    @GetMapping("/{id}")
    public InstrumentoDTO obtenerPorId(@PathVariable Long id) {
        return instrumentoService.obtenerPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InstrumentoDTO crear(@Valid @RequestBody InstrumentoDTO dto) {
        return instrumentoService.crear(dto);
    }

    @PutMapping("/{id}")
    public InstrumentoDTO actualizar(@PathVariable Long id, @Valid @RequestBody InstrumentoDTO dto) {
        return instrumentoService.actualizar(id, dto);
    }

    @PatchMapping("/{id}/despachar")
    public InstrumentoDTO despachar(@PathVariable Long id) {
        return instrumentoService.despachar(id);
    }

    @PatchMapping("/{id}/recibir")
    public InstrumentoDTO recibir(@PathVariable Long id) {
        return instrumentoService.recibir(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        instrumentoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
