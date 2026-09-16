package com.kevin.backend.controller;

import com.kevin.backend.dto.ClienteDTO;
import com.kevin.backend.service.ClienteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    public List<ClienteDTO> listar(@RequestParam(required = false) String buscar) {
        if (buscar != null && !buscar.isBlank()) {
            return clienteService.buscar(buscar);
        }
        return clienteService.listar();
    }

    @GetMapping("/{id}")
    public ClienteDTO obtener(@PathVariable Long id) {
        return clienteService.obtenerPorId(id);
    }

    @PostMapping
    public ClienteDTO crear(@Valid @RequestBody ClienteDTO dto) {
        return clienteService.crear(dto);
    }

    @PutMapping("/{id}")
    public ClienteDTO actualizar(@PathVariable Long id, @Valid @RequestBody ClienteDTO dto) {
        return clienteService.actualizar(id, dto);
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        clienteService.eliminar(id);
    }
}