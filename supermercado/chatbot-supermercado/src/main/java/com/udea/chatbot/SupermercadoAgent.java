package com.udea.chatbot;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.mcp.McpToolset;
import com.google.adk.tools.mcp.StreamableHttpServerParameters;

import java.time.Duration;
import java.util.List;

/**
 * Chatbot de pedidos a domicilio. Es un agente de Google ADK (modelo Gemini) cuyo único
 * acceso al negocio son las herramientas que descubre por MCP en el servidor
 * supermercado-mcp: no conoce la base de datos ni tiene SQL ni precios en su código.
 *
 * AdkWebServer localiza el campo estático ROOT_AGENT en las clases compiladas.
 */
public class SupermercadoAgent {

    private static final String MCP_URL =
            System.getenv().getOrDefault("MCP_SERVER_URL", "http://localhost:8089/mcp");

    private static final String MODELO =
            System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.5-flash-lite");

    private static final String INSTRUCCIONES = """
            Eres "Merca", el asistente de pedidos a domicilio de un supermercado colombiano.
            Atiendes en español, con trato amable y respuestas breves.

            Reglas:
            - Todo lo que digas sobre productos, precios, stock y pedidos debe salir de las herramientas.
              Nunca inventes productos, ids, precios ni existencias.
            - Para ubicar lo que pide el cliente usa buscarProductos o listarProductos y toma de ahí el id.
              Si hay varias coincidencias, pregunta cuál quiere. Si no hay stock, dilo y ofrece una alternativa
              de la misma categoría.
            - Antes de crearPedido necesitas nombre, dirección de entrega y teléfono. Pídelos si faltan; no los inventes.
            - Un pedido se arma así: crearPedido, luego agregarProducto por cada producto. Usa cambiarCantidad
              para corregir o quitar (cantidad 0).
            - Antes de confirmar muestra el resumen de verPedido: productos, cantidades, subtotal, domicilio y total.
              Llama confirmarPedido solo cuando el cliente acepte de forma explícita. Igual con cancelarPedido.
            - Los precios están en pesos colombianos; escríbelos como $4.200. El domicilio cuesta $5.000 y es
              gratis desde $80.000 de compra; puedes avisar cuánto falta para el domicilio gratis.
            - Si una herramienta devuelve un error, explícalo al cliente en palabras sencillas y propón cómo seguir.
            - Si te piden algo ajeno al supermercado, indica con amabilidad que solo ayudas con pedidos.
            """;

    public static BaseAgent ROOT_AGENT = buildAgent();

    private static BaseAgent buildAgent() {
        StreamableHttpServerParameters params =
                StreamableHttpServerParameters.builder()
                        .url(MCP_URL)
                        .timeout(Duration.ofSeconds(30))
                        .build();

        try {
            List<BaseTool> herramientas = new McpToolset(params).getTools(null)
                    .toList()
                    .blockingGet();

            System.out.println("Herramientas MCP descubiertas: " + herramientas.size());
            herramientas.forEach(t -> System.out.println("  - " + t.name()));

            return LlmAgent.builder()
                    .name("SupermercadoAgent")
                    .model(MODELO)
                    .description("Chatbot para hacer pedidos de supermercado a domicilio")
                    .instruction(INSTRUCCIONES)
                    .tools(herramientas)
                    .build();

        } catch (Exception e) {
            throw new IllegalStateException(
                    "No fue posible conectar con el servidor MCP en " + MCP_URL
                            + ". Arranque primero supermercado-mcp.", e);
        }
    }
}
