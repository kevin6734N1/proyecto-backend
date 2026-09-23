package com.kevin.backend.controller;

import com.kevin.backend.dto.InformeTecnicoDTO;
import com.kevin.backend.model.EstadoInforme;
import com.kevin.backend.service.InformeTecnicoService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/informes-tecnicos")
public class InformeTecnicoController {

    private final InformeTecnicoService informeTecnicoService;

    public InformeTecnicoController(InformeTecnicoService informeTecnicoService) {
        this.informeTecnicoService = informeTecnicoService;
    }

    @GetMapping
    public List<InformeTecnicoDTO> listar() {
        return informeTecnicoService.listar();
    }

    @GetMapping("/{id}")
    public InformeTecnicoDTO obtenerPorId(@PathVariable Long id) {
        return informeTecnicoService.obtenerPorId(id);
    }

    @PatchMapping("/{id}/pdf-cargado")
    public InformeTecnicoDTO marcarPdfCargado(@PathVariable Long id) {
        return informeTecnicoService.marcarPdfCargado(id);
    }

    @PatchMapping("/{id}/estado")
    public InformeTecnicoDTO actualizarEstado(@PathVariable Long id, @RequestParam EstadoInforme estado) {
        return informeTecnicoService.actualizarEstado(id, estado);
    }
}
