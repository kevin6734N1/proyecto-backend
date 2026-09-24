package com.kevin.backend.service;

import com.kevin.backend.exception.CorrelativoAgotadoException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Reintenta toda la operación en una transacción nueva. La transacción anterior
 * termina antes del siguiente intento; reintentar dentro de una transacción
 * marcada rollback-only no serviría.
 */
@Component
public class CorrelativoRetry {
    private static final int MAX_INTENTOS = 3;
    private final TransactionTemplate transaccion;

    public CorrelativoRetry(PlatformTransactionManager manager) {
        this.transaccion = new TransactionTemplate(manager);
        this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T ejecutar(Supplier<T> operacion) {
        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
            try {
                return transaccion.execute(estado -> operacion.get());
            } catch (DataIntegrityViolationException | TransientDataAccessException e) {
                if (intento == MAX_INTENTOS) {
                    throw new CorrelativoAgotadoException(
                            "No se pudo asignar un correlativo único tras " + MAX_INTENTOS + " intentos.", e);
                }
            }
        }
        throw new IllegalStateException("No se pudo completar el reintento de correlativo.");
    }
}
