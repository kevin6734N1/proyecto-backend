package com.kevin.backend.service;

import com.kevin.backend.dto.ExpedienteDTO;
import com.kevin.backend.model.Cliente;
import com.kevin.backend.model.EstadoExpediente;
import com.kevin.backend.model.Expediente;
import com.kevin.backend.repository.ClienteRepository;
import com.kevin.backend.repository.ExpedienteRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;

@Service
public class ExpedienteService {

    private final ExpedienteRepository expedienteRepository;
    private final ClienteRepository clienteRepository;

    public ExpedienteService(ExpedienteRepository expedienteRepository, ClienteRepository clienteRepository) {
        this.expedienteRepository = expedienteRepository;
        this.clienteRepository = clienteRepository;
    }

    public List<ExpedienteDTO> listar() {
        return expedienteRepository.findAll().stream().map(this::toDTO).toList();
    }

    public ExpedienteDTO obtenerPorId(Long id) {
        Expediente exp = expedienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expediente no encontrado con id " + id));
        return toDTO(exp);
    }

    public ExpedienteDTO crear(Long clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado con id " + clienteId));

        Expediente expediente = new Expediente();
        expediente.setNumero(generarNumero());
        expediente.setFecha(LocalDate.now());
        expediente.setCliente(cliente);
        expediente.setEstado(EstadoExpediente.EN_PROCESO);

        return toDTO(expedienteRepository.save(expediente));
    }

    public ExpedienteDTO cambiarEstado(Long id, EstadoExpediente nuevoEstado) {
        Expediente exp = expedienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expediente no encontrado con id " + id));
        exp.setEstado(nuevoEstado);
        return toDTO(expedienteRepository.save(exp));
    }

    private String generarNumero() {
        String prefijo = "E" + String.valueOf(Year.now().getValue()).substring(2);
        long correlativo = expedienteRepository.countByNumeroStartingWith(prefijo) + 1;
        return prefijo + String.format("%05d", correlativo);
    }

    private ExpedienteDTO toDTO(Expediente exp) {
        return new ExpedienteDTO(exp.getId(), exp.getNumero(), exp.getFecha(),
                exp.getCliente().getId(), exp.getCliente().getRazonSocial(), exp.getEstado());
    }
}