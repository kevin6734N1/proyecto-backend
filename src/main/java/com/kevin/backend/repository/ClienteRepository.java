package com.kevin.backend.repository;

import com.kevin.backend.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    List<Cliente> findByRazonSocialContainingIgnoreCase(String texto);
    boolean existsByRuc(String ruc);
}