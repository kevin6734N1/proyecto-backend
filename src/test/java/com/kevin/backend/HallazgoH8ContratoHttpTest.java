package com.kevin.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:gesminh8contrato;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class HallazgoH8ContratoHttpTest {
    @LocalServerPort
    private int puerto;

    @Test
    void duplicidadDeNegocioConservaElContrato400() throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + puerto + "/api/clientes");
        String payload = "{\"razonSocial\":\"Cliente E16\",\"ruc\":\"20999999991\"}";
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        try (HttpClient cliente = HttpClient.newHttpClient()) {
            HttpResponse<String> primero = cliente.send(request, HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> duplicado = cliente.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("### E16 POST /api/clientes " + payload
                    + " -> HTTP " + primero.statusCode() + " " + primero.body());
            System.out.println("### E16 POST /api/clientes duplicado " + payload
                    + " -> HTTP " + duplicado.statusCode() + " " + duplicado.body());
            assertEquals(200, primero.statusCode());
            assertEquals(400, duplicado.statusCode());
            assertEquals("{\"mensaje\":\"Ya existe un cliente con RUC 20999999991\"}", duplicado.body());
        }
    }

    @Test
    void idsInexistentesConservanElContrato400() throws Exception {
        Map<String, String> casos = Map.of(
                "/api/clientes/999999999", "Cliente no encontrado con id 999999999",
                "/api/cotizaciones/999999999", "Cotización no encontrada con id 999999999",
                "/api/ordenes-trabajo/999999999", "Orden de Trabajo no encontrada con id 999999999",
                "/api/calibraciones/999999999", "Calibración no encontrada con id 999999999",
                "/api/informes-tecnicos/999999999", "Informe Técnico no encontrado con id 999999999");
        try (HttpClient cliente = HttpClient.newHttpClient()) {
            for (var caso : casos.entrySet()) {
                HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + puerto + caso.getKey())).GET().build();
                HttpResponse<String> respuesta = cliente.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println("### E16 GET " + caso.getKey() + " -> HTTP "
                        + respuesta.statusCode() + " " + respuesta.body());
                assertEquals(400, respuesta.statusCode(), caso.getKey());
                assertEquals("{\"mensaje\":\"" + caso.getValue() + "\"}", respuesta.body(), caso.getKey());
            }
        }
    }
}
