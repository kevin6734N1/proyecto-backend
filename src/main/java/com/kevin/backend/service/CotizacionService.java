package com.kevin.backend.service;

import com.kevin.backend.dto.CotizacionDTO;
import com.kevin.backend.dto.DetalleCotizacionDTO;
import com.kevin.backend.model.*;
import com.kevin.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class CotizacionService {

    private final CotizacionRepository cotizacionRepository;
    private final ClienteRepository clienteRepository;
    private final ContactoRepository contactoRepository;
    private final ExpedienteRepository expedienteRepository;
    private final ProductoRepository productoRepository;
    private final ServicioRepository servicioRepository;
    private final CorrelativoRetry correlativos;

    public CotizacionService(CotizacionRepository cotizacionRepository,
                              ClienteRepository clienteRepository,
                              ContactoRepository contactoRepository,
                              ExpedienteRepository expedienteRepository,
                              ProductoRepository productoRepository,
                              ServicioRepository servicioRepository,
                              CorrelativoRetry correlativos) {
        this.cotizacionRepository = cotizacionRepository;
        this.clienteRepository = clienteRepository;
        this.contactoRepository = contactoRepository;
        this.expedienteRepository = expedienteRepository;
        this.productoRepository = productoRepository;
        this.servicioRepository = servicioRepository;
        this.correlativos = correlativos;
    }

    public List<CotizacionDTO> listar() {
        return cotizacionRepository.findAll().stream().map(this::toDTO).toList();
    }

    public CotizacionDTO obtenerPorId(Long id) {
        return toDTO(buscarEntidadPorId(id));
    }

    public CotizacionDTO crear(CotizacionDTO dto) {
        return correlativos.ejecutar(() -> crearUnaVez(dto));
    }

    private CotizacionDTO crearUnaVez(CotizacionDTO dto) {
        Cliente cliente = clienteRepository.findById(dto.clienteId())
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado con id " + dto.clienteId()));

        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setCliente(cliente);
        cotizacion.setFechaEmision(dto.fechaEmision() != null ? dto.fechaEmision() : LocalDate.now());
        cotizacion.setFechaVencimiento(dto.fechaVencimiento());
        cotizacion.setObservaciones(dto.observaciones());
        cotizacion.setEstado(EstadoCotizacion.BORRADOR);

        if (dto.contactoId() != null) {
            cotizacion.setContacto(contactoRepository.findById(dto.contactoId())
                    .orElseThrow(() -> new RuntimeException("Contacto no encontrado con id " + dto.contactoId())));
        }
        if (dto.expedienteId() != null) {
            cotizacion.setExpediente(expedienteRepository.findById(dto.expedienteId())
                    .orElseThrow(() -> new RuntimeException("Expediente no encontrado con id " + dto.expedienteId())));
        }

        cotizacion.setCodigo(generarCodigo());

        List<DetalleCotizacion> detalles = new ArrayList<>();
        for (DetalleCotizacionDTO detalleDTO : dto.detalles()) {
            detalles.add(construirDetalle(detalleDTO, cotizacion));
        }
        cotizacion.setDetalles(detalles);
        cotizacion.setMontoTotal(calcularMontoTotal(detalles));

        Cotizacion guardada = cotizacionRepository.saveAndFlush(cotizacion);
        return toDTO(guardada);
    }

    @Transactional
    public CotizacionDTO actualizarEstado(Long id, EstadoCotizacion nuevoEstado) {
        Cotizacion cotizacion = buscarEntidadPorId(id);
        cotizacion.setEstado(nuevoEstado);
        return toDTO(cotizacionRepository.save(cotizacion));
    }

    private DetalleCotizacion construirDetalle(DetalleCotizacionDTO dto, Cotizacion cotizacion) {
        boolean tieneProducto = dto.productoId() != null;
        boolean tieneServicio = dto.servicioId() != null;

        if (tieneProducto == tieneServicio) {
            throw new IllegalArgumentException(
                "Cada línea de detalle debe referenciar exactamente un producto o un servicio, pero no ambos ni ninguno."
            );
        }

        DetalleCotizacion detalle = new DetalleCotizacion();
        detalle.setCotizacion(cotizacion);
        detalle.setCantidad(dto.cantidad());
        detalle.setDescuentoPorcentaje(dto.descuentoPorcentaje() != null ? dto.descuentoPorcentaje() : BigDecimal.ZERO);
        detalle.setCostoAdicional(dto.costoAdicional() != null ? dto.costoAdicional() : BigDecimal.ZERO);
        detalle.setComentarios(dto.comentarios());
        detalle.setMarcaInstrumento(dto.marcaInstrumento());
        detalle.setModeloInstrumento(dto.modeloInstrumento());
        detalle.setSerieInstrumento(dto.serieInstrumento());

        BigDecimal precioLista;
        if (tieneProducto) {
            Producto producto = productoRepository.findById(dto.productoId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado con id " + dto.productoId()));
            detalle.setProducto(producto);
            detalle.setCodigoSnapshot(producto.getCodigo());
            detalle.setNombreSnapshot(producto.getNombre());
            precioLista = producto.getPrecio();
        } else {
            Servicio servicio = servicioRepository.findById(dto.servicioId())
                    .orElseThrow(() -> new RuntimeException("Servicio no encontrado con id " + dto.servicioId()));
            detalle.setServicio(servicio);
            detalle.setCodigoSnapshot(servicio.getCodigo());
            detalle.setNombreSnapshot(servicio.getNombre());
            precioLista = servicio.getPrecioVenta();
        }

        detalle.setPrecioUnitario(precioLista);
        detalle.setSubtotal(calcularSubtotalLinea(detalle));
        return detalle;
    }

    private BigDecimal calcularSubtotalLinea(DetalleCotizacion detalle) {
        BigDecimal precioConDescuento = detalle.getPrecioUnitario()
                .multiply(BigDecimal.ONE.subtract(
                        detalle.getDescuentoPorcentaje().divide(BigDecimal.valueOf(100))
                ));
        BigDecimal subtotal = precioConDescuento
                .multiply(BigDecimal.valueOf(detalle.getCantidad()))
                .add(detalle.getCostoAdicional());
        return subtotal.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcularMontoTotal(List<DetalleCotizacion> detalles) {
        return detalles.stream()
                .map(DetalleCotizacion::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String generarCodigo() {
        LocalDate hoy = LocalDate.now();
        String yy = String.format("%02d", hoy.getYear() % 100);
        String mm = String.format("%02d", hoy.getMonthValue());
        String prefijoAnual = "COI" + yy; // cuenta todo el año, sin importar el mes

        long correlativo = cotizacionRepository.countByCodigoStartingWith(prefijoAnual) + 1;
        return "COI" + yy + mm + String.format("%02d", correlativo);
    }

    private Cotizacion buscarEntidadPorId(Long id) {
        return cotizacionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cotización no encontrada con id " + id));
    }

    private CotizacionDTO toDTO(Cotizacion c) {
        List<DetalleCotizacionDTO> detallesDTO = c.getDetalles().stream()
                .map(this::detalleToDTO)
                .toList();

        return new CotizacionDTO(
                c.getId(),
                c.getCodigo(),
                c.getCliente().getId(),
                c.getCliente().getRazonSocial(),
                c.getContacto() != null ? c.getContacto().getId() : null,
                c.getContacto() != null ? c.getContacto().getNombre() : null,
                c.getExpediente() != null ? c.getExpediente().getId() : null,
                c.getExpediente() != null ? c.getExpediente().getNumero() : null,
                c.getFechaEmision(),
                c.getFechaVencimiento(),
                c.getEstado(),
                c.getMontoTotal(),
                c.getObservaciones(),
                detallesDTO
        );
    }

    private DetalleCotizacionDTO detalleToDTO(DetalleCotizacion d) {
        return new DetalleCotizacionDTO(
                d.getId(),
                d.getProducto() != null ? d.getProducto().getId() : null,
                d.getServicio() != null ? d.getServicio().getId() : null,
                d.getCodigoSnapshot(),
                d.getNombreSnapshot(),
                d.getPrecioUnitario(),
                d.getDescuentoPorcentaje(),
                d.getCantidad(),
                d.getSubtotal(),
                d.getCostoAdicional(),
                d.getComentarios(),
                d.getMarcaInstrumento(),
                d.getModeloInstrumento(),
                d.getSerieInstrumento()
        );
    }
}
