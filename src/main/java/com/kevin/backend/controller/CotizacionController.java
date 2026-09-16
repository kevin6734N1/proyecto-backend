package com.kevin.backend.controller;

import com.kevin.backend.dto.CotizacionDTO;
import com.kevin.backend.model.EstadoCotizacion;
import com.kevin.backend.service.CotizacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cotizaciones")
public class CotizacionController {

    private final CotizacionService cotizacionService;

    public CotizacionController(CotizacionService cotizacionService) {
        this.cotizacionService = cotizacionService;
    }

    @GetMapping
    public List<CotizacionDTO> listar() {
        return cotizacionService.listar();
    }

    @GetMapping("/{id}")
    public CotizacionDTO obtenerPorId(@PathVariable Long id) {
        return cotizacionService.obtenerPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CotizacionDTO crear(@Valid @RequestBody CotizacionDTO dto) {
        return cotizacionService.crear(dto);
    }

    @PatchMapping("/{id}/estado")
    public CotizacionDTO actualizarEstado(@PathVariable Long id, @RequestParam EstadoCotizacion estado) {
        return cotizacionService.actualizarEstado(id, estado);
    }
}
