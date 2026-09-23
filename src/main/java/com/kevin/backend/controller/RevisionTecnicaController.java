package com.kevin.backend.controller;

import com.kevin.backend.dto.RevisionTecnicaDTO;
import com.kevin.backend.model.ResultadoRevision;
import com.kevin.backend.service.RevisionTecnicaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/revisiones-tecnicas")
public class RevisionTecnicaController {

    private final RevisionTecnicaService revisionService;

    public RevisionTecnicaController(RevisionTecnicaService revisionService) {
        this.revisionService = revisionService;
    }

    @GetMapping
    public List<RevisionTecnicaDTO> listarPorCalibracion(@RequestParam Long calibracionId) {
        return revisionService.listarPorCalibracion(calibracionId);
    }

    @GetMapping("/{id}")
    public RevisionTecnicaDTO obtenerPorId(@PathVariable Long id) {
        return revisionService.obtenerPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RevisionTecnicaDTO crear(@Valid @RequestBody RevisionTecnicaDTO dto) {
        return revisionService.crear(dto);
    }

    @PatchMapping("/{id}/resultado")
    public RevisionTecnicaDTO registrarResultado(@PathVariable Long id,
                                                   @RequestParam ResultadoRevision resultado,
                                                   @RequestParam(required = false) String observaciones) {
        return revisionService.registrarResultado(id, resultado, observaciones);
    }
}
