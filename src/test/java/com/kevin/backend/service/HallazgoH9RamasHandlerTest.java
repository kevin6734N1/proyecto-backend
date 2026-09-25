package com.kevin.backend.service;

import com.kevin.backend.controller.ExpedienteController;
import com.kevin.backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.FileSystemException;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * H9 (ramas del handler): una excepción de negocio y un bug real de servidor
 * pasan por el mismo @ExceptionHandler(RuntimeException.class) y salen con
 * el mismo código HTTP. Mocks separados por caso para no pisar stubbings.
 */
class HallazgoH9RamasHandlerTest {

    private MockMvc httpCon(ExpedienteService servicio) {
        return MockMvcBuilders.standaloneSetup(new ExpedienteController(servicio))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void h9b_negocioResponde400() throws Exception {
        ExpedienteService servicio = mock(ExpedienteService.class);
        Mockito.when(servicio.crear(anyLong()))
                .thenThrow(new IllegalArgumentException("Cliente no encontrado con id 99999"));
        var respuesta = httpCon(servicio).perform(post("/api/expedientes").param("clienteId", "99999"))
                .andReturn().getResponse();
        System.out.println("### H9b excepción de NEGOCIO (IllegalArgumentException) → HTTP "
                + respuesta.getStatus() + " " + respuesta.getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals(400, respuesta.getStatus());
    }

    @Test
    void h9b_falloDeServidorResponde400() throws Exception {
        ExpedienteService servicio = mock(ExpedienteService.class);
        Mockito.when(servicio.crear(anyLong())).thenThrow(new IllegalStateException(
                "No se pudo almacenar el PDF firmado.", new FileSystemException("data/pdf-firmados")));
        var respuesta = httpCon(servicio).perform(post("/api/expedientes").param("clienteId", "99999"))
                .andReturn().getResponse();
        System.out.println("### H9b fallo de SERVIDOR (IllegalStateException + IOException de disco) → HTTP "
                + respuesta.getStatus() + " " + respuesta.getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals(400, respuesta.getStatus(),
                "H9: el handler respondió distinto para un bug real de servidor");
    }
}
