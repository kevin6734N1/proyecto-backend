package com.kevin.backend.service;

import com.kevin.backend.dto.ProductoDTO;
import com.kevin.backend.model.Marca;
import com.kevin.backend.model.Modelo;
import com.kevin.backend.model.Producto;
import com.kevin.backend.repository.DetalleCotizacionRepository;
import com.kevin.backend.repository.MarcaRepository;
import com.kevin.backend.repository.ModeloRepository;
import com.kevin.backend.repository.ProductoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final MarcaRepository marcaRepository;
    private final ModeloRepository modeloRepository;
    private final DetalleCotizacionRepository detalleCotizacionRepository;

    public ProductoService(ProductoRepository productoRepository,
                            MarcaRepository marcaRepository,
                            ModeloRepository modeloRepository,
                            DetalleCotizacionRepository detalleCotizacionRepository) {
        this.productoRepository = productoRepository;
        this.marcaRepository = marcaRepository;
        this.modeloRepository = modeloRepository;
        this.detalleCotizacionRepository = detalleCotizacionRepository;
    }

    public List<ProductoDTO> listar() {
        return productoRepository.findAll().stream().map(this::toDTO).toList();
    }

    public List<ProductoDTO> buscar(String texto) {
        return productoRepository.findByNombreContainingIgnoreCase(texto).stream()
                .map(this::toDTO)
                .toList();
    }

    public ProductoDTO obtenerPorId(Long id) {
        return toDTO(buscarEntidadPorId(id));
    }

    public ProductoDTO crear(ProductoDTO dto) {
        if (productoRepository.existsByCodigo(dto.codigo())) {
            throw new IllegalArgumentException("Ya existe un producto con el código " + dto.codigo());
        }
        Producto producto = new Producto();
        aplicarDatos(producto, dto);
        return toDTO(productoRepository.save(producto));
    }

    public ProductoDTO actualizar(Long id, ProductoDTO dto) {
        Producto producto = buscarEntidadPorId(id);

        if (!producto.getCodigo().equals(dto.codigo()) && productoRepository.existsByCodigo(dto.codigo())) {
            throw new IllegalArgumentException("Ya existe un producto con el código " + dto.codigo());
        }

        aplicarDatos(producto, dto);
        return toDTO(productoRepository.save(producto));
    }

    public void eliminar(Long id) {
        Producto producto = buscarEntidadPorId(id);
        if (detalleCotizacionRepository.existsByProductoId(id)) {
            producto.setActivo(false);
            productoRepository.save(producto);
        } else {
            productoRepository.delete(producto);
        }
    }

    private void aplicarDatos(Producto producto, ProductoDTO dto) {
        Marca marca = marcaRepository.findById(dto.marcaId())
                .orElseThrow(() -> new IllegalArgumentException("Marca no encontrada con id " + dto.marcaId()));
        Modelo modelo = modeloRepository.findById(dto.modeloId())
                .orElseThrow(() -> new IllegalArgumentException("Modelo no encontrado con id " + dto.modeloId()));

        producto.setCodigo(dto.codigo());
        producto.setNombre(dto.nombre());
        producto.setMarca(marca);
        producto.setModelo(modelo);
        producto.setPrecio(dto.precio());
        producto.setStock(dto.stock());
    }

    private Producto buscarEntidadPorId(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado con id " + id));
    }

    private ProductoDTO toDTO(Producto p) {
        return new ProductoDTO(
                p.getId(),
                p.getCodigo(),
                p.getNombre(),
                p.getMarca().getId(),
                p.getMarca().getNombre(),
                p.getModelo().getId(),
                p.getModelo().getNombre(),
                p.getPrecio(),
                p.getStock()
        );
    }
}
