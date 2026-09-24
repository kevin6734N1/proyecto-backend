package com.kevin.backend.exception;

public class CorrelativoAgotadoException extends RuntimeException {
    public CorrelativoAgotadoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
