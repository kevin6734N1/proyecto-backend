package com.kevin.backend.service;

import com.kevin.backend.controller.ExpedienteController;
import com.kevin.backend.exception.CorrelativoAgotadoException;
import com.kevin.backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class CorrelativoErrorHttpTest {
    @Test
    void conflictoDeCorrelativoDevuelve409ConMensaje() throws Exception {
        ExpedienteService servicio = mock(ExpedienteService.class);
        when(servicio.crear(anyLong())).thenThrow(new CorrelativoAgotadoException(
                "No se pudo asignar un correlativo único tras 3 intentos.",
                new IllegalStateException("contención")));
        MockMvc http = MockMvcBuilders.standaloneSetup(new ExpedienteController(servicio))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        var respuesta = http.perform(post("/api/expedientes").param("clienteId", "7"))
                .andReturn().getResponse();
        System.out.println("### HTTP POST /api/expedientes?clienteId=7");
        System.out.println("### HTTP " + respuesta.getStatus() + " " + respuesta.getContentAsString());
        assertEquals(409, respuesta.getStatus());
        assertEquals("{\"mensaje\":\"No se pudo asignar un correlativo único tras 3 intentos.\"}",
                respuesta.getContentAsString());
    }
}
