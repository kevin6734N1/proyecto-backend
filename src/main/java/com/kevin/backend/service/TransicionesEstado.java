package com.kevin.backend.service;

import com.kevin.backend.model.*;

import java.util.Map;
import java.util.Set;

/** Transiciones permitidas por cada puerta de mutación del flujo Gesmin. */
public final class TransicionesEstado {
    private TransicionesEstado() {}

    public static final Map<Enum<?>, Set<Enum<?>>> COTIZACION = Map.of(
            EstadoCotizacion.BORRADOR, Set.of(EstadoCotizacion.ENVIADA, EstadoCotizacion.APROBADA, EstadoCotizacion.RECHAZADA),
            EstadoCotizacion.ENVIADA, Set.of(EstadoCotizacion.APROBADA, EstadoCotizacion.RECHAZADA),
            EstadoCotizacion.APROBADA, Set.of(),
            EstadoCotizacion.RECHAZADA, Set.of());

    public static final Map<Enum<?>, Set<Enum<?>>> CALIBRACION = Map.of(
            EstadoCalibracion.PROGRAMADA, Set.of(EstadoCalibracion.EN_PROCESO),
            EstadoCalibracion.EN_PROCESO, Set.of(EstadoCalibracion.COMPLETADA, EstadoCalibracion.CANCELADA),
            EstadoCalibracion.COMPLETADA, Set.of(EstadoCalibracion.EN_PROCESO),
            EstadoCalibracion.CANCELADA, Set.of());

    public static final Map<Enum<?>, Set<Enum<?>>> ORDEN_INTERNA = Map.of(
            EstadoOrdenTrabajo.PENDIENTE, Set.of(EstadoOrdenTrabajo.EN_PROCESO, EstadoOrdenTrabajo.EN_ESPERA_CLIENTE, EstadoOrdenTrabajo.CANCELADA),
            EstadoOrdenTrabajo.EN_PROCESO, Set.of(EstadoOrdenTrabajo.COMPLETADA, EstadoOrdenTrabajo.CANCELADA),
            EstadoOrdenTrabajo.EN_ESPERA_CLIENTE, Set.of(EstadoOrdenTrabajo.PENDIENTE, EstadoOrdenTrabajo.CANCELADA),
            EstadoOrdenTrabajo.COMPLETADA, Set.of(),
            EstadoOrdenTrabajo.CANCELADA, Set.of());

    public static final Map<Enum<?>, Set<Enum<?>>> ORDEN_PATCH = Map.of(
            EstadoOrdenTrabajo.PENDIENTE, Set.of(EstadoOrdenTrabajo.EN_PROCESO, EstadoOrdenTrabajo.CANCELADA),
            EstadoOrdenTrabajo.EN_PROCESO, Set.of(EstadoOrdenTrabajo.COMPLETADA, EstadoOrdenTrabajo.CANCELADA),
            EstadoOrdenTrabajo.EN_ESPERA_CLIENTE, Set.of(EstadoOrdenTrabajo.CANCELADA),
            EstadoOrdenTrabajo.COMPLETADA, Set.of(),
            EstadoOrdenTrabajo.CANCELADA, Set.of());

    public static final Map<Enum<?>, Set<Enum<?>>> EXPEDIENTE = Map.of(
            EstadoExpediente.EN_PROCESO, Set.of(EstadoExpediente.RECHAZADO, EstadoExpediente.EN_ESPERA, EstadoExpediente.CERRADO),
            EstadoExpediente.EN_ESPERA, Set.of(EstadoExpediente.EN_PROCESO, EstadoExpediente.RECHAZADO),
            EstadoExpediente.RECHAZADO, Set.of(),
            EstadoExpediente.CERRADO, Set.of());

    public static void validarTransicion(Enum<?> actual, Enum<?> nuevo,
                                          Map<Enum<?>, Set<Enum<?>>> permitidas) {
        if (actual == nuevo) return;
        if (actual == null || nuevo == null || !permitidas.getOrDefault(actual, Set.of()).contains(nuevo)) {
            throw new IllegalArgumentException("Transición de estado no permitida: " + actual + " -> " + nuevo + ".");
        }
    }
}
