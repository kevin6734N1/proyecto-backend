package com.kevin.backend.repository;

import com.kevin.backend.model.CorrelativoContador;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CorrelativoContadorRepository extends JpaRepository<CorrelativoContador, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CorrelativoContador c where c.prefijo = :prefijo")
    Optional<CorrelativoContador> bloquear(@Param("prefijo") String prefijo);
}
