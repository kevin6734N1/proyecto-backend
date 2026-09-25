package com.kevin.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:gesmine15http;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class ErrorResponseHttpTest {

    @LocalServerPort
    private int puerto;

    @Test
    void rutaInexistenteResponde404SinTrace() throws Exception {
        verificarSinTrace("/api/no-existe-e15", false, 404);
    }

    @Test
    void parametroObligatorioAusenteResponde400SinTrace() throws Exception {
        verificarSinTrace("/api/expedientes", true, 400);
    }

    private void verificarSinTrace(String ruta, boolean post, int esperado) throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + puerto + ruta);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json");
        HttpRequest request = post
                ? builder.POST(HttpRequest.BodyPublishers.noBody()).build()
                : builder.GET().build();
        try (HttpClient cliente = HttpClient.newHttpClient()) {
            HttpResponse<String> response = cliente.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("### E15 " + (post ? "POST " : "GET ") + ruta
                    + " → HTTP " + response.statusCode()
                    + " " + response.body());
            assertEquals(esperado, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"));
            assertTrue(response.body().contains("\"status\":" + esperado));
            assertFalse(response.body().contains("\"trace\""), "El body expone el stack trace");
            assertFalse(response.body().contains("\"exception\""), "El body expone la clase interna");
        }
    }
}
