package com.kevin.backend.service;

import com.kevin.backend.model.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransicionesEstadoTest {
    @Test
    void cotizacionPermiteAprobacionDirectaPeroNoReabrirTerminal() {
        assertDoesNotThrow(() -> TransicionesEstado.validarTransicion(
                EstadoCotizacion.BORRADOR, EstadoCotizacion.APROBADA, TransicionesEstado.COTIZACION));
        assertThrows(IllegalArgumentException.class, () -> TransicionesEstado.validarTransicion(
                EstadoCotizacion.APROBADA, EstadoCotizacion.RECHAZADA, TransicionesEstado.COTIZACION));
        assertDoesNotThrow(() -> TransicionesEstado.validarTransicion(
                EstadoCotizacion.APROBADA, EstadoCotizacion.APROBADA, TransicionesEstado.COTIZACION));
    }

    @Test
    void ordenUsaPuertasDistintasParaLoopInternoYPatch() {
        assertDoesNotThrow(() -> TransicionesEstado.validarTransicion(
                EstadoOrdenTrabajo.PENDIENTE, EstadoOrdenTrabajo.EN_ESPERA_CLIENTE,
                TransicionesEstado.ORDEN_INTERNA));
        assertDoesNotThrow(() -> TransicionesEstado.validarTransicion(
                EstadoOrdenTrabajo.EN_ESPERA_CLIENTE, EstadoOrdenTrabajo.PENDIENTE,
                TransicionesEstado.ORDEN_INTERNA));
        assertThrows(IllegalArgumentException.class, () -> TransicionesEstado.validarTransicion(
                EstadoOrdenTrabajo.EN_ESPERA_CLIENTE, EstadoOrdenTrabajo.PENDIENTE,
                TransicionesEstado.ORDEN_PATCH));
        assertThrows(IllegalArgumentException.class, () -> TransicionesEstado.validarTransicion(
                EstadoOrdenTrabajo.COMPLETADA, EstadoOrdenTrabajo.CANCELADA,
                TransicionesEstado.ORDEN_PATCH));
    }

    @Test
    void calibracionPermiteCorreccionYExpedienteCerradoEsTerminal() {
        assertDoesNotThrow(() -> TransicionesEstado.validarTransicion(
                EstadoCalibracion.COMPLETADA, EstadoCalibracion.EN_PROCESO,
                TransicionesEstado.CALIBRACION));
        assertThrows(IllegalArgumentException.class, () -> TransicionesEstado.validarTransicion(
                EstadoCalibracion.CANCELADA, EstadoCalibracion.EN_PROCESO,
                TransicionesEstado.CALIBRACION));
        assertThrows(IllegalArgumentException.class, () -> TransicionesEstado.validarTransicion(
                EstadoExpediente.CERRADO, EstadoExpediente.EN_PROCESO,
                TransicionesEstado.EXPEDIENTE));
    }
}
