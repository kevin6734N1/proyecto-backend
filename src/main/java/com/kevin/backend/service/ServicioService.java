package com.kevin.backend.service;

import com.kevin.backend.dto.ServicioDTO;
import com.kevin.backend.model.Servicio;
import com.kevin.backend.repository.DetalleCotizacionRepository;
import com.kevin.backend.repository.ServicioRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ServicioService {

    private final ServicioRepository servicioRepository;
    private final DetalleCotizacionRepository detalleCotizacionRepository;

    public ServicioService(ServicioRepository servicioRepository,
                            DetalleCotizacionRepository detalleCotizacionRepository) {
        this.servicioRepository = servicioRepository;
        this.detalleCotizacionRepository = detalleCotizacionRepository;
    }

    public List<ServicioDTO> listar() {
        return servicioRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    public List<ServicioDTO> buscar(String texto) {
        return servicioRepository.findByNombreContainingIgnoreCase(texto).stream()
                .map(this::toDTO)
                .toList();
    }

    public ServicioDTO obtenerPorId(Long id) {
        Servicio servicio = buscarEntidadPorId(id);
        return toDTO(servicio);
    }

    public ServicioDTO crear(ServicioDTO dto) {
        if (servicioRepository.existsByCodigo(dto.codigo())) {
            throw new RuntimeException("Ya existe un servicio con el código " + dto.codigo());
        }
        Servicio servicio = toEntity(dto);
        servicio.setId(null);
        Servicio guardado = servicioRepository.save(servicio);
        return toDTO(guardado);
    }

    public ServicioDTO actualizar(Long id, ServicioDTO dto) {
        Servicio servicio = buscarEntidadPorId(id);

        if (!servicio.getCodigo().equals(dto.codigo()) && servicioRepository.existsByCodigo(dto.codigo())) {
            throw new RuntimeException("Ya existe un servicio con el código " + dto.codigo());
        }

        servicio.setCodigo(dto.codigo());
        servicio.setNombre(dto.nombre());
        servicio.setTipoServicio(dto.tipoServicio());
        servicio.setTipoEquipoAplicable(dto.tipoEquipoAplicable());
        servicio.setDescripcion(dto.descripcion());
        servicio.setPrecioVenta(dto.precioVenta());

        Servicio actualizado = servicioRepository.save(servicio);
        return toDTO(actualizado);
    }

    public void eliminar(Long id) {
        Servicio servicio = buscarEntidadPorId(id);
        if (detalleCotizacionRepository.existsByServicioId(id)) {
            servicio.setActivo(false);
            servicioRepository.save(servicio);
        } else {
            servicioRepository.delete(servicio);
        }
    }

    private Servicio buscarEntidadPorId(Long id) {
        return servicioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Servicio no encontrado con id " + id));
    }

    private ServicioDTO toDTO(Servicio s) {
        return new ServicioDTO(
                s.getId(),
                s.getCodigo(),
                s.getNombre(),
                s.getTipoServicio(),
                s.getTipoEquipoAplicable(),
                s.getDescripcion(),
                s.getPrecioVenta()
        );
    }

    private Servicio toEntity(ServicioDTO dto) {
        Servicio s = new Servicio();
        s.setId(dto.id());
        s.setCodigo(dto.codigo());
        s.setNombre(dto.nombre());
        s.setTipoServicio(dto.tipoServicio());
        s.setTipoEquipoAplicable(dto.tipoEquipoAplicable());
        s.setDescripcion(dto.descripcion());
        s.setPrecioVenta(dto.precioVenta());
        return s;
    }
}
