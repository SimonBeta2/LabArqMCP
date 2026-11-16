package com.udea.agente_mcp;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.mcp.McpToolset;
import com.google.adk.tools.mcp.StreamableHttpServerParameters;

import java.time.Duration;
import java.util.List;

/**
 * Agente de Google ADK que consume el MISMO servidor MCP de Spring AI que usa
 * Claude Desktop. No conoce la base de datos ni el código Java del servidor:
 * descubre las herramientas por MCP (tools/list) y las invoca con tools/call.
 *
 * AdkWebServer busca el campo estático ROOT_AGENT en las clases compiladas.
 */
public class TareaAgent {

    private static final String MCP_URL =
            System.getenv().getOrDefault("MCP_SERVER_URL", "http://localhost:8088/mcp");

    // La guía usa gemini-2.5-flash, pero Google ya no lo ofrece a claves nuevas
    // (la API responde 404). gemini-3.8-flash funciona pero tarda 30-50 s por llamada en el
    // nivel gratuito; gemini-3.5-flash-lite responde en menos de 1 s. Se cambia con GEMINI_MODEL.
    private static final String MODELO =
            System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.5-flash-lite");

    public static BaseAgent ROOT_AGENT = buildAgent();

    private static BaseAgent buildAgent() {
        StreamableHttpServerParameters params =
                StreamableHttpServerParameters.builder()
                        .url(MCP_URL)
                        .timeout(Duration.ofSeconds(30))
                        .build();

        McpToolset mcpToolset = new McpToolset(params);

        try {
            List<BaseTool> mcpTools = mcpToolset.getTools(null)
                    .toList()
                    .blockingGet();

            System.out.println("Herramientas MCP descubiertas: " + mcpTools.size());
            mcpTools.forEach(t -> System.out.println("  - " + t.name()));

            return LlmAgent.builder()
                    .name("TareaAgent")
                    .model(MODELO)
                    .description("Agente para administrar tareas")
                    .instruction("Usa las herramientas MCP de tareas para consultar, crear, actualizar y "
                            + "administrar pendientes. Las fechas van en formato ISO yyyy-MM-dd. "
                            + "Antes de eliminar, confirma el identificador con el usuario.")
                    .tools(mcpTools)
                    .build();

        } catch (Exception e) {
            throw new IllegalStateException(
                    "No fue posible conectar con el servidor MCP en " + MCP_URL, e);
        }
    }

    /** Verificación rápida sin interfaz web: solo carga el agente y lista sus herramientas. */
    public static void main(String[] args) {
        System.out.println("Agente listo: " + ROOT_AGENT.name());
        System.exit(0);
    }
}
