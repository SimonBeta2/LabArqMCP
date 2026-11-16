-- Parte A de la guía, adaptada a PostgreSQL: base de datos del laboratorio MCP.
-- Se ejecuta a mano con psql (Spring Boot no lo corre: spring.sql.init.mode=never):
--   psql -h localhost -U postgres -f src/main/resources/schema.sql
-- Se puede ejecutar varias veces: no duplica la base, la tabla ni los datos.

-- PostgreSQL no tiene CREATE DATABASE IF NOT EXISTS; \gexec ejecuta el CREATE solo si falta.
SELECT 'CREATE DATABASE tareas_mcp ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'tareas_mcp')\gexec

\connect tareas_mcp

CREATE TABLE IF NOT EXISTS tarea (
    id BIGSERIAL PRIMARY KEY,
    completado BOOLEAN DEFAULT FALSE,
    fecha VARCHAR(255) NULL,
    titulo VARCHAR(255) NOT NULL,
    version BIGINT DEFAULT 0
);

INSERT INTO tarea (titulo, fecha, completado, version)
SELECT * FROM (VALUES
    ('Preparar charla MCP', '2026-04-20', FALSE, 0::BIGINT),
    ('Revisar slides',      '2026-04-21', FALSE, 0::BIGINT),
    ('Probar demo en vivo', '2026-04-22', TRUE,  0::BIGINT)
) AS datos(titulo, fecha, completado, version)
WHERE NOT EXISTS (SELECT 1 FROM tarea);

SELECT id, titulo, fecha, completado FROM tarea ORDER BY id;
