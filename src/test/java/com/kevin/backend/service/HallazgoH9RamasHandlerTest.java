package com.kevin.backend.service;

import com.kevin.backend.controller.ExpedienteController;
import com.kevin.backend.controller.MarcaController;
import com.kevin.backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.FileSystemException;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * H9: separa los errores de negocio de los fallos de servidor.
 * Mocks separados por caso para no pisar stubbings.
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
        org.junit.jupiter.api.Assertions.assertEquals(
                "{\"mensaje\":\"Cliente no encontrado con id 99999\"}", respuesta.getContentAsString());
    }

    @Test
    void h9b_validacionDeBeanResponde400() throws Exception {
        MockMvc http = MockMvcBuilders.standaloneSetup(new MarcaController(mock(MarcaService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var respuesta = http.perform(post("/api/marcas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andReturn().getResponse();
        System.out.println("### H9b Bean Validation POST /api/marcas → HTTP "
                + respuesta.getStatus() + " " + respuesta.getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals(400, respuesta.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(
                "{\"nombre\":\"El nombre de la marca es obligatorio\"}", respuesta.getContentAsString());
    }

    @Test
    void h9b_falloDeServidorResponde500() throws Exception {
        ExpedienteService servicio = mock(ExpedienteService.class);
        Mockito.when(servicio.crear(anyLong())).thenThrow(new IllegalStateException(
                "No se pudo almacenar el PDF firmado.", new FileSystemException("data/pdf-firmados")));
        var respuesta = httpCon(servicio).perform(post("/api/expedientes").param("clienteId", "99999"))
                .andReturn().getResponse();
        System.out.println("### H9b fallo de SERVIDOR (IllegalStateException + IOException de disco) → HTTP "
                + respuesta.getStatus() + " " + respuesta.getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals(500, respuesta.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(
                "{\"mensaje\":\"Error interno del servidor.\"}", respuesta.getContentAsString());
    }
}
