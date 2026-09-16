package com.kevin.backend.repository;

import com.kevin.backend.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    List<Producto> findByNombreContainingIgnoreCase(String texto);
    boolean existsByCodigo(String codigo);
}
