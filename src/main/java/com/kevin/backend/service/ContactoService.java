package com.kevin.backend.service;

import com.kevin.backend.dto.ContactoDTO;
import com.kevin.backend.model.Cliente;
import com.kevin.backend.model.Contacto;
import com.kevin.backend.repository.ClienteRepository;
import com.kevin.backend.repository.ContactoRepository;
import org.springframework.stereotype.Service;

@Service
public class ContactoService {

    private final ContactoRepository contactoRepository;
    private final ClienteRepository clienteRepository;

    public ContactoService(ContactoRepository contactoRepository, ClienteRepository clienteRepository) {
        this.contactoRepository = contactoRepository;
        this.clienteRepository = clienteRepository;
    }

    public ContactoDTO agregar(Long clienteId, ContactoDTO dto) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado con id " + clienteId));

        Contacto contacto = new Contacto();
        contacto.setNombre(dto.nombre());
        contacto.setEmail(dto.email());
        contacto.setTelefono(dto.telefono());
        contacto.setCliente(cliente);

        Contacto guardado = contactoRepository.save(contacto);
        return toDTO(guardado);
    }

    public ContactoDTO actualizar(Long contactoId, ContactoDTO dto) {
        Contacto contacto = contactoRepository.findById(contactoId)
                .orElseThrow(() -> new RuntimeException("Contacto no encontrado con id " + contactoId));
        contacto.setNombre(dto.nombre());
        contacto.setEmail(dto.email());
        contacto.setTelefono(dto.telefono());
        return toDTO(contactoRepository.save(contacto));
    }

    public void eliminar(Long contactoId) {
        contactoRepository.deleteById(contactoId);
    }

    private ContactoDTO toDTO(Contacto contacto) {
        return new ContactoDTO(contacto.getId(), contacto.getNombre(), contacto.getEmail(), contacto.getTelefono());
    }
}