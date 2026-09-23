package com.kevin.backend.controller;

import com.kevin.backend.dto.HerramientaDTO;
import com.kevin.backend.service.HerramientaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/herramientas")
public class HerramientaController {

    private final HerramientaService herramientaService;

    public HerramientaController(HerramientaService herramientaService) {
        this.herramientaService = herramientaService;
    }

    @GetMapping
    public List<HerramientaDTO> listar(@RequestParam(required = false) String buscar,
                                        @RequestParam(defaultValue = "false") boolean soloActivas) {
        if (buscar != null && !buscar.isBlank()) {
            return herramientaService.buscar(buscar);
        }
        return herramientaService.listar(soloActivas);
    }

    @GetMapping("/{id}")
    public HerramientaDTO obtenerPorId(@PathVariable Long id) {
        return herramientaService.obtenerPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HerramientaDTO crear(@Valid @RequestBody HerramientaDTO dto) {
        return herramientaService.crear(dto);
    }

    @PutMapping("/{id}")
    public HerramientaDTO actualizar(@PathVariable Long id, @Valid @RequestBody HerramientaDTO dto) {
        return herramientaService.actualizar(id, dto);
    }

    @PatchMapping("/{id}/desactivar")
    public HerramientaDTO desactivar(@PathVariable Long id) {
        herramientaService.desactivar(id);
        return herramientaService.obtenerPorId(id);
    }
}
