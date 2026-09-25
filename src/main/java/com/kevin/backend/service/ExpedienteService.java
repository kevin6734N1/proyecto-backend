package com.kevin.backend.service;

import com.kevin.backend.dto.ExpedienteDTO;
import com.kevin.backend.model.Cliente;
import com.kevin.backend.model.EstadoExpediente;
import com.kevin.backend.model.Expediente;
import com.kevin.backend.repository.ClienteRepository;
import com.kevin.backend.repository.ExpedienteRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ExpedienteService {

    private final ExpedienteRepository expedienteRepository;
    private final ClienteRepository clienteRepository;
    private final CorrelativoRetry correlativos;
    private final CorrelativoService contador;

    public ExpedienteService(ExpedienteRepository expedienteRepository, ClienteRepository clienteRepository,
                             CorrelativoRetry correlativos, CorrelativoService contador) {
        this.expedienteRepository = expedienteRepository;
        this.clienteRepository = clienteRepository;
        this.correlativos = correlativos;
        this.contador = contador;
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
        return correlativos.ejecutar(() -> crearUnaVez(clienteId));
    }

    private ExpedienteDTO crearUnaVez(Long clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado con id " + clienteId));

        Expediente expediente = new Expediente();
        expediente.setNumero(generarNumero());
        expediente.setFecha(LocalDate.now());
        expediente.setCliente(cliente);
        expediente.setEstado(EstadoExpediente.EN_PROCESO);

        return toDTO(expedienteRepository.saveAndFlush(expediente));
    }

    public ExpedienteDTO cambiarEstado(Long id, EstadoExpediente nuevoEstado) {
        Expediente exp = expedienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expediente no encontrado con id " + id));
        if (exp.getEstado() == nuevoEstado) return toDTO(exp);
        TransicionesEstado.validarTransicion(exp.getEstado(), nuevoEstado, TransicionesEstado.EXPEDIENTE);
        exp.setEstado(nuevoEstado);
        return toDTO(expedienteRepository.save(exp));
    }

    private String generarNumero() {
        String yy = String.format("%02d", LocalDate.now().getYear() % 100);
        return contador.siguiente("E", () -> expedienteRepository.countByNumeroStartingWith("E" + yy));
    }

    private ExpedienteDTO toDTO(Expediente exp) {
        return new ExpedienteDTO(exp.getId(), exp.getNumero(), exp.getFecha(),
                exp.getCliente().getId(), exp.getCliente().getRazonSocial(), exp.getEstado());
    }
}