package com.kevin.backend.controller;

import com.kevin.backend.dto.ContactoDTO;
import com.kevin.backend.service.ContactoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clientes/{clienteId}/contactos")
public class ContactoController {

    private final ContactoService contactoService;

    public ContactoController(ContactoService contactoService) {
        this.contactoService = contactoService;
    }

    @PostMapping
    public ContactoDTO agregar(@PathVariable Long clienteId, @Valid @RequestBody ContactoDTO dto) {
        return contactoService.agregar(clienteId, dto);
    }

    @PutMapping("/{contactoId}")
    public ContactoDTO actualizar(@PathVariable Long clienteId, @PathVariable Long contactoId, @Valid @RequestBody ContactoDTO dto) {
        return contactoService.actualizar(contactoId, dto);
    }

    @DeleteMapping("/{contactoId}")
    public void eliminar(@PathVariable Long clienteId, @PathVariable Long contactoId) {
        contactoService.eliminar(contactoId);
    }
}