package com.kevin.backend.controller;

import com.kevin.backend.dto.CalibracionDTO;
import com.kevin.backend.dto.PuntoCalibracionDTO;
import com.kevin.backend.model.EstadoCalibracion;
import com.kevin.backend.service.CalibracionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/calibraciones")
public class CalibracionController {

    private final CalibracionService calibracionService;

    public CalibracionController(CalibracionService calibracionService) {
        this.calibracionService = calibracionService;
    }

    @GetMapping
    public List<CalibracionDTO> listar() {
        return calibracionService.listar();
    }

    @GetMapping("/{id}")
    public CalibracionDTO obtenerPorId(@PathVariable Long id) {
        return calibracionService.obtenerPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CalibracionDTO crear(@Valid @RequestBody CalibracionDTO dto) {
        return calibracionService.crear(dto);
    }

    @PatchMapping("/{id}/estado")
    public CalibracionDTO actualizarEstado(@PathVariable Long id, @RequestParam EstadoCalibracion estado) {
        return calibracionService.actualizarEstado(id, estado);
    }

    @PutMapping("/{id}/mediciones")
    public CalibracionDTO registrarMediciones(@PathVariable Long id,
                                               @Valid @RequestBody List<PuntoCalibracionDTO> puntos) {
        return calibracionService.registrarMediciones(id, puntos);
    }
}
