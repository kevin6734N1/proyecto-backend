package com.kevin.backend.service;

import com.kevin.backend.model.CorrelativoContador;
import com.kevin.backend.repository.CorrelativoContadorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.function.LongSupplier;

/** El contador anual se bloquea en la BD hasta confirmar el documento que lo usa. */
@Service
public class CorrelativoService {
    private final CorrelativoContadorRepository contadores;

    public CorrelativoService(CorrelativoContadorRepository contadores) {
        this.contadores = contadores;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String siguiente(String tipo, LongSupplier historicos) {
        LocalDate hoy = LocalDate.now();
        String yy = String.format("%02d", hoy.getYear() % 100);
        String prefijo = tipo + yy;
        CorrelativoContador contador = contadores.bloquear(prefijo).orElseGet(() -> {
            CorrelativoContador nuevo = new CorrelativoContador();
            nuevo.setPrefijo(prefijo);
            nuevo.setUltimo(historicos.getAsLong());
            return contadores.saveAndFlush(nuevo);
        });
        contador.setUltimo(contador.getUltimo() + 1);
        contadores.saveAndFlush(contador);
        return prefijo + String.format("%02d", hoy.getMonthValue())
                + String.format("%02d", contador.getUltimo());
    }
}
