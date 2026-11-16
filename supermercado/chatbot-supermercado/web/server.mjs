// Interfaz web del chatbot. Sin dependencias: solo Node.js (>= 18).
//
//   node web/server.mjs        ->  http://localhost:3000
//
// Sirve index.html y reenvía cada mensaje al agente ADK (AdkWebServer, puerto 8081).
// El navegador nunca habla directamente con ADK, con Gemini ni con el servidor MCP:
//
//   navegador -> este servidor -> agente ADK (Gemini) -> MCP -> Spring Boot -> PostgreSQL

import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { randomUUID } from "node:crypto";
import { fileURLToPath } from "node:url";
import path from "node:path";

const PUERTO = Number(process.env.PORT || 3000);
const ADK = process.env.ADK_URL || "http://localhost:8081";
const APP = "SupermercadoAgent";
const USUARIO = "cliente-web";
const AQUI = path.dirname(fileURLToPath(import.meta.url));

const sesiones = new Set(); // sesiones ya creadas en ADK

async function adk(ruta, cuerpo) {
  const r = await fetch(ADK + ruta, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(cuerpo),
  });
  const texto = await r.text();
  if (!r.ok) throw new Error(`ADK respondió ${r.status}: ${texto.slice(0, 300)}`);
  return texto ? JSON.parse(texto) : null;
}

async function conversar(sesion, mensaje) {
  if (!sesiones.has(sesion)) {
    await adk(`/apps/${APP}/users/${USUARIO}/sessions/${sesion}`, {});
    sesiones.add(sesion);
  }
  const eventos = await adk("/run", {
    appName: APP,
    userId: USUARIO,
    sessionId: sesion,
    newMessage: { role: "user", parts: [{ text: mensaje }] },
  });

  // De los eventos del agente se separa el texto final y las herramientas MCP que usó.
  const textos = [];
  const herramientas = [];
  for (const evento of eventos || []) {
    for (const parte of evento.content?.parts || []) {
      if (parte.functionCall) {
        herramientas.push({ nombre: parte.functionCall.name, argumentos: parte.functionCall.args || {} });
      } else if (parte.text && !parte.thought) {
        textos.push(parte.text);
      }
    }
  }
  return { respuesta: textos.join("\n").trim() || "(sin respuesta)", herramientas };
}

function leerJson(req) {
  return new Promise((resolver, rechazar) => {
    let datos = "";
    req.on("data", (c) => {
      datos += c;
      if (datos.length > 20_000) rechazar(new Error("Mensaje demasiado largo"));
    });
    req.on("end", () => {
      try { resolver(JSON.parse(datos || "{}")); } catch { rechazar(new Error("JSON inválido")); }
    });
    req.on("error", rechazar);
  });
}

function json(res, estado, cuerpo) {
  res.writeHead(estado, { "Content-Type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(cuerpo));
}

createServer(async (req, res) => {
  try {
    if (req.method === "GET" && (req.url === "/" || req.url === "/index.html")) {
      res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
      return res.end(await readFile(path.join(AQUI, "index.html")));
    }
    if (req.method === "POST" && req.url === "/api/chat") {
      const { sesion, mensaje } = await leerJson(req);
      if (typeof mensaje !== "string" || !mensaje.trim()) {
        return json(res, 400, { error: "Escriba un mensaje" });
      }
      const id = /^[\w-]{8,64}$/.test(sesion || "") ? sesion : randomUUID();
      return json(res, 200, { sesion: id, ...(await conversar(id, mensaje.trim())) });
    }
    json(res, 404, { error: "No encontrado" });
  } catch (e) {
    console.error(e);
    const sinAgente = e.cause?.code === "ECONNREFUSED";
    json(res, 502, {
      error: sinAgente
        ? "El agente no está disponible. Arranque el servidor MCP (8089) y el agente ADK (8081)."
        : "No se pudo procesar el mensaje: " + e.message,
    });
  }
}).listen(PUERTO, () => {
  console.log(`Chatbot del supermercado en http://localhost:${PUERTO}  (agente ADK: ${ADK})`);
});
