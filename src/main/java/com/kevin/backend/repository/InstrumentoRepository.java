package com.kevin.backend.repository;

import com.kevin.backend.model.Instrumento;
import com.kevin.backend.model.UbicacionInstrumento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InstrumentoRepository extends JpaRepository<Instrumento, Long> {
    List<Instrumento> findByClienteId(Long clienteId);
    List<Instrumento> findByUbicacion(UbicacionInstrumento ubicacion);
}
