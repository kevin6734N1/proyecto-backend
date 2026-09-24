package com.kevin.backend.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate ddl-auto=update agrega columnas, pero no amplía los valores del ENUM
 * de H2 ya existente. La base de desarrollo conserva los cuatro valores antiguos.
 */
@Component
public class InformeEstadoSchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public InformeEstadoSchemaMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("ALTER TABLE informes_tecnicos ALTER COLUMN estado "
                + "ENUM('APROBADO', 'ENVIADO', 'GENERADO', 'PDF_CARGADO', 'ANULADO') NOT NULL");
    }
}
