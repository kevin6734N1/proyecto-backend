package com.kevin.backend.controller;

import com.kevin.backend.dto.OrdenDeTrabajoDTO;
import com.kevin.backend.model.EstadoOrdenTrabajo;
import com.kevin.backend.service.OrdenDeTrabajoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ordenes-trabajo")
public class OrdenDeTrabajoController {

    private final OrdenDeTrabajoService ordenDeTrabajoService;

    public OrdenDeTrabajoController(OrdenDeTrabajoService ordenDeTrabajoService) {
        this.ordenDeTrabajoService = ordenDeTrabajoService;
    }

    @GetMapping
    public List<OrdenDeTrabajoDTO> listar() {
        return ordenDeTrabajoService.listar();
    }

    @GetMapping("/{id}")
    public OrdenDeTrabajoDTO obtenerPorId(@PathVariable Long id) {
        return ordenDeTrabajoService.obtenerPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrdenDeTrabajoDTO crear(@Valid @RequestBody OrdenDeTrabajoDTO dto) {
        return ordenDeTrabajoService.crear(dto);
    }

    @PatchMapping("/{id}/estado")
    public OrdenDeTrabajoDTO actualizarEstado(@PathVariable Long id, @RequestParam EstadoOrdenTrabajo estado) {
        return ordenDeTrabajoService.actualizarEstado(id, estado);
    }
}
