package com.kevin.backend.service;

import com.kevin.backend.dto.InstrumentoDTO;
import com.kevin.backend.model.Cliente;
import com.kevin.backend.model.Instrumento;
import com.kevin.backend.model.UbicacionInstrumento;
import com.kevin.backend.repository.ClienteRepository;
import com.kevin.backend.repository.InstrumentoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InstrumentoService {

    private final InstrumentoRepository instrumentoRepository;
    private final ClienteRepository clienteRepository;

    public InstrumentoService(InstrumentoRepository instrumentoRepository,
                               ClienteRepository clienteRepository) {
        this.instrumentoRepository = instrumentoRepository;
        this.clienteRepository = clienteRepository;
    }

    public List<InstrumentoDTO> listar() {
        return instrumentoRepository.findAll().stream().map(this::toDTO).toList();
    }

    public List<InstrumentoDTO> listarPorUbicacion(UbicacionInstrumento ubicacion) {
        return instrumentoRepository.findByUbicacion(ubicacion).stream().map(this::toDTO).toList();
    }

    public List<InstrumentoDTO> listarPorCliente(Long clienteId) {
        return instrumentoRepository.findByClienteId(clienteId).stream().map(this::toDTO).toList();
    }

    public InstrumentoDTO obtenerPorId(Long id) {
        return toDTO(buscarEntidadPorId(id));
    }

    public InstrumentoDTO crear(InstrumentoDTO dto) {
        Cliente cliente = clienteRepository.findById(dto.clienteId())
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado con id " + dto.clienteId()));

        Instrumento instrumento = new Instrumento();
        aplicarDatos(instrumento, dto, cliente);
        instrumento.setUbicacion(UbicacionInstrumento.UBICADO_EN_CLIENTE);

        return toDTO(instrumentoRepository.save(instrumento));
    }

    public InstrumentoDTO actualizar(Long id, InstrumentoDTO dto) {
        Instrumento instrumento = buscarEntidadPorId(id);
        Cliente cliente = clienteRepository.findById(dto.clienteId())
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado con id " + dto.clienteId()));

        aplicarDatos(instrumento, dto, cliente);
        return toDTO(instrumentoRepository.save(instrumento));
    }

    public InstrumentoDTO despachar(Long id) {
        Instrumento instrumento = buscarEntidadPorId(id);
        instrumento.setUbicacion(UbicacionInstrumento.EN_TRANSITO);
        return toDTO(instrumentoRepository.save(instrumento));
    }

    public InstrumentoDTO recibir(Long id) {
        Instrumento instrumento = buscarEntidadPorId(id);
        instrumento.setUbicacion(UbicacionInstrumento.UBICADO_EN_GESMIN);
        return toDTO(instrumentoRepository.save(instrumento));
    }

    public void eliminar(Long id) {
        instrumentoRepository.delete(buscarEntidadPorId(id));
    }

    private void aplicarDatos(Instrumento instrumento, InstrumentoDTO dto, Cliente cliente) {
        instrumento.setTipo(dto.tipo());
        instrumento.setMarca(dto.marca());
        instrumento.setModelo(dto.modelo());
        instrumento.setSerie(dto.serie());
        instrumento.setCodigoCliente(dto.codigoCliente());
        instrumento.setCliente(cliente);
    }

    private Instrumento buscarEntidadPorId(Long id) {
        return instrumentoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Instrumento no encontrado con id " + id));
    }

    private InstrumentoDTO toDTO(Instrumento i) {
        return new InstrumentoDTO(
                i.getId(), i.getTipo(), i.getMarca(), i.getModelo(), i.getSerie(),
                i.getCodigoCliente(), i.getCliente().getId(), i.getCliente().getRazonSocial(),
                i.getUbicacion()
        );
    }
}
