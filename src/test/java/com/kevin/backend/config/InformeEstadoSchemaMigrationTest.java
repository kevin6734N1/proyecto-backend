package com.kevin.backend.config;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InformeEstadoSchemaMigrationTest {
    @Test
    void ampliaEnumHistoricoSinPerderInformes() {
        JdbcDataSource data = new JdbcDataSource();
        data.setURL("jdbc:h2:mem:gesminenum;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(data);
        jdbc.execute("CREATE TABLE informes_tecnicos (id BIGINT PRIMARY KEY, "
                + "estado ENUM('APROBADO', 'ENVIADO', 'GENERADO', 'PDF_CARGADO') NOT NULL)");
        jdbc.update("INSERT INTO informes_tecnicos(id, estado) VALUES (1, 'ENVIADO')");
        InformeEstadoSchemaMigration migracion = new InformeEstadoSchemaMigration(jdbc);
        migracion.run(new DefaultApplicationArguments());
        migracion.run(new DefaultApplicationArguments()); // idempotente
        jdbc.update("UPDATE informes_tecnicos SET estado='ANULADO' WHERE id=1");
        String estado = jdbc.queryForObject(
                "SELECT estado FROM informes_tecnicos WHERE id=1", String.class);
        System.out.println("### MIGRACION enum antiguo ENVIADO -> " + estado
                + " (fila histórica conservada)");
        assertEquals("ANULADO", estado);
    }
}
