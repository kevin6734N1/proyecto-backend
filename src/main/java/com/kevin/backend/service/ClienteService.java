package com.kevin.backend.service;

import com.kevin.backend.dto.ClienteDTO;
import com.kevin.backend.dto.ContactoDTO;
import com.kevin.backend.model.Cliente;
import com.kevin.backend.model.Contacto;
import com.kevin.backend.repository.ClienteRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;

    public ClienteService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    public List<ClienteDTO> listar() {
        return clienteRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    public ClienteDTO obtenerPorId(Long id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado con id " + id));
        return toDTO(cliente);
    }

    public ClienteDTO crear(ClienteDTO dto) {
        if (clienteRepository.existsByRuc(dto.ruc())) {
            throw new RuntimeException("Ya existe un cliente con RUC " + dto.ruc());
        }
        Cliente cliente = new Cliente();
        cliente.setRazonSocial(dto.razonSocial());
        cliente.setRuc(dto.ruc());
        cliente.setDireccion(dto.direccion());
        cliente.setRubro(dto.rubro());
        return toDTO(clienteRepository.save(cliente));
    }

    public ClienteDTO actualizar(Long id, ClienteDTO dto) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado con id " + id));
        cliente.setRazonSocial(dto.razonSocial());
        cliente.setDireccion(dto.direccion());
        cliente.setRubro(dto.rubro());
        return toDTO(clienteRepository.save(cliente));
    }

    public void eliminar(Long id) {
        clienteRepository.deleteById(id);
    }

    public List<ClienteDTO> buscar(String texto) {
        return clienteRepository.findByRazonSocialContainingIgnoreCase(texto).stream()
                .map(this::toDTO)
                .toList();
    }

    private ClienteDTO toDTO(Cliente cliente) {
        List<ContactoDTO> contactos = cliente.getContactos().stream()
                .map(c -> new ContactoDTO(c.getId(), c.getNombre(), c.getEmail(), c.getTelefono()))
                .toList();
        return new ClienteDTO(cliente.getId(), cliente.getRazonSocial(), cliente.getRuc(),
                cliente.getDireccion(), cliente.getRubro(), contactos);
    }
}