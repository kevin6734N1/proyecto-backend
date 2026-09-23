package com.kevin.backend.controller;

import com.kevin.backend.dto.EvaluacionAptitudDTO;
import com.kevin.backend.model.ResultadoEvaluacion;
import com.kevin.backend.service.EvaluacionAptitudService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/evaluaciones-aptitud")
public class EvaluacionAptitudController {

    private final EvaluacionAptitudService evaluacionService;

    public EvaluacionAptitudController(EvaluacionAptitudService evaluacionService) {
        this.evaluacionService = evaluacionService;
    }

    @GetMapping
    public List<EvaluacionAptitudDTO> listarPorOrden(@RequestParam Long ordenDeTrabajoId) {
        return evaluacionService.listarPorOrden(ordenDeTrabajoId);
    }

    @GetMapping("/{id}")
    public EvaluacionAptitudDTO obtenerPorId(@PathVariable Long id) {
        return evaluacionService.obtenerPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EvaluacionAptitudDTO crear(@Valid @RequestBody EvaluacionAptitudDTO dto) {
        return evaluacionService.crear(dto);
    }

    @PatchMapping("/{id}/resultado")
    public EvaluacionAptitudDTO registrarResultado(@PathVariable Long id,
                                                     @RequestParam ResultadoEvaluacion resultado,
                                                     @RequestParam(required = false) String observaciones) {
        return evaluacionService.registrarResultado(id, resultado, observaciones);
    }

    public record RespuestaClienteRequest(@NotNull Boolean autorizaContinuar, String comunicacionCliente) {}

    @PostMapping("/{id}/respuesta-cliente")
    public EvaluacionAptitudDTO registrarRespuestaCliente(@PathVariable Long id,
                                                            @Valid @RequestBody RespuestaClienteRequest body) {
        return evaluacionService.registrarRespuestaCliente(id, body.autorizaContinuar(), body.comunicacionCliente());
    }
}
