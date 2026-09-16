package com.kevin.backend.controller;

import com.kevin.backend.dto.ServicioDTO;
import com.kevin.backend.service.ServicioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/servicios")
public class ServicioController {

    private final ServicioService servicioService;

    public ServicioController(ServicioService servicioService) {
        this.servicioService = servicioService;
    }

    @GetMapping
    public List<ServicioDTO> listar(@RequestParam(required = false) String buscar) {
        if (buscar != null && !buscar.isBlank()) {
            return servicioService.buscar(buscar);
        }
        return servicioService.listar();
    }

    @GetMapping("/{id}")
    public ServicioDTO obtenerPorId(@PathVariable Long id) {
        return servicioService.obtenerPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServicioDTO crear(@Valid @RequestBody ServicioDTO dto) {
        return servicioService.crear(dto);
    }

    @PutMapping("/{id}")
    public ServicioDTO actualizar(@PathVariable Long id, @Valid @RequestBody ServicioDTO dto) {
        return servicioService.actualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        servicioService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
