package com.kevin.backend.controller;

import com.kevin.backend.dto.ExpedienteDTO;
import com.kevin.backend.model.EstadoExpediente;
import com.kevin.backend.service.ExpedienteService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/expedientes")
public class ExpedienteController {

    private final ExpedienteService expedienteService;

    public ExpedienteController(ExpedienteService expedienteService) {
        this.expedienteService = expedienteService;
    }

    @GetMapping
    public List<ExpedienteDTO> listar() {
        return expedienteService.listar();
    }

    @GetMapping("/{id}")
    public ExpedienteDTO obtener(@PathVariable Long id) {
        return expedienteService.obtenerPorId(id);
    }

    @PostMapping
    public ExpedienteDTO crear(@RequestParam Long clienteId) {
        return expedienteService.crear(clienteId);
    }

    @PatchMapping("/{id}/estado")
    public ExpedienteDTO cambiarEstado(@PathVariable Long id, @RequestParam EstadoExpediente estado) {
        return expedienteService.cambiarEstado(id, estado);
    }
}