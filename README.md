# Laboratorio MCP · Arquitectura de Software 2026-2

Construcción e integración de un servidor MCP (Model Context Protocol) con Spring Boot y Spring AI,
consumido desde Claude Desktop y desde un agente de Google ADK, más el ejercicio propuesto:
un chatbot de pedidos de supermercado a domicilio.

**Estudiante:** Simón Betancur Sosa · **Profesor:** Diego José Luis Botia · Universidad de Antioquia

El informe con el paso a paso y las evidencias está en [Entregable_Lab_MCP.docx](Entregable_Lab_MCP.docx).

## Contenido

| Carpeta | Qué es | Puerto |
|---|---|---|
| [`mcpexample/`](mcpexample) | Servidor MCP de tareas (laboratorio guiado): Spring Boot 3.4.7 + Spring AI 1.1.4, 9 herramientas `@Tool` | 8088 |
| [`agente-mcp/`](agente-mcp) | Agente Google ADK (`TareaAgent`) que usa el servidor de tareas desde Gemini | 8080 |
| [`supermercado/supermercado-mcp/`](supermercado/supermercado-mcp) | Servidor MCP del ejercicio propuesto: catálogo y pedidos, 10 herramientas | 8089 |
| [`supermercado/chatbot-supermercado/`](supermercado/chatbot-supermercado) | Chatbot: agente Google ADK (`SupermercadoAgent`) e interfaz web en Node.js | 8081 y 3000 |

```
Claude Desktop ─(mcp-remote)─┐
Agente ADK (Gemini) ─────────┼─► Servidor MCP (Spring AI, /mcp) ─► Spring Data JPA ─► PostgreSQL
curl / MCP Inspector ────────┘
```

## Requisitos

- JDK 21 o superior (probado con JDK 24)
- PostgreSQL (probado con la versión 17)
- Node.js 18 o superior
- Una clave de Gemini de [Google AI Studio](https://aistudio.google.com/apikey)

Ningún secreto está en el repositorio. Se pasan por variables de entorno:

| Variable | Uso |
|---|---|
| `DB_USER` | Usuario de PostgreSQL (por defecto `postgres`) |
| `PWD_UDEA` | Clave de PostgreSQL |
| `GOOGLE_API_KEY` | Clave de Gemini para los agentes ADK |
| `GEMINI_MODEL` | Opcional. Modelo de Gemini (por defecto `gemini-3.5-flash-lite`) |

## Ejecución

Los comandos son para PowerShell. Cada servidor va en su propia ventana.

### 1. Bases de datos

```powershell
psql -h localhost -U postgres -f mcpexample/src/main/resources/schema.sql
psql -h localhost -U postgres -c "CREATE DATABASE supermercado_mcp"
```

La primera crea `tareas_mcp` con sus datos de ejemplo. Las tablas del supermercado las crea Hibernate
y el catálogo (25 productos) se carga solo en el primer arranque.

### 2. Laboratorio guiado: tareas

```powershell
# Servidor MCP  ->  http://localhost:8088/mcp
cd mcpexample
$env:DB_USER = "postgres"; $env:PWD_UDEA = "SU_CLAVE"
.\mvnw.cmd spring-boot:run

# Agente ADK  ->  http://localhost:8080
cd agente-mcp
$env:GOOGLE_API_KEY = "SU_CLAVE_DE_GEMINI"
.\mvnw.cmd clean compile exec:java@runAdkWebServer
```

Para Claude Desktop, con la aplicación completamente cerrada:

```powershell
node configurar-claude-desktop.js
```

Registra el servidor `tareas-mcp` usando el puente `mcp-remote` (con `--quitar` lo elimina).

### 3. Ejercicio propuesto: supermercado

```powershell
# Servidor MCP  ->  http://localhost:8089/mcp
cd supermercado\supermercado-mcp
$env:DB_USER = "postgres"; $env:PWD_UDEA = "SU_CLAVE"
.\mvnw.cmd spring-boot:run

# Agente del chatbot  ->  http://localhost:8081
cd supermercado\chatbot-supermercado
$env:GOOGLE_API_KEY = "SU_CLAVE_DE_GEMINI"
.\mvnw.cmd clean compile exec:java@runAdkWebServer

# Interfaz web del chatbot  ->  http://localhost:3000
node web\server.mjs
```

## Pruebas

Las pruebas usan H2 en memoria: no necesitan PostgreSQL ni variables de entorno.

```powershell
cd mcpexample;                      .\mvnw.cmd clean test   # 11 pruebas
cd supermercado\supermercado-mcp;   .\mvnw.cmd clean test   # 10 pruebas
```

## Diferencias con la guía

- **PostgreSQL** en lugar de MySQL (la guía permite elegir el motor).
- **Google ADK 1.14.0** en ambos artefactos: la guía mezcla 0.6.0 y 1.0.0, que no funcionan con los modelos Gemini actuales.
- **Modelo configurable**: `gemini-2.5-flash` ya no está disponible para claves nuevas.
- Dos herramientas adicionales (`contarTareas`, `listarCompletadas`) y validación de fecha ISO, de las actividades del laboratorio.
