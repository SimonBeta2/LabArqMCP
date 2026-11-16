// Registra el servidor MCP del laboratorio en Claude Desktop (Parte D de la guía).
//
// Claude Desktop reescribe su archivo de configuración mientras está abierto, así
// que este script se debe ejecutar con Claude Desktop COMPLETAMENTE CERRADO
// (clic derecho en el ícono de la bandeja del sistema -> Salir):
//
//   node configurar-claude-desktop.js            agrega "tareas-mcp"
//   node configurar-claude-desktop.js --quitar   lo elimina
//
// El puente mcp-remote convierte STDIO (lo que Claude Desktop sabe lanzar) en
// Streamable HTTP (lo que expone el servidor Spring AI en /mcp).

const fs = require("fs");
const path = require("path");
const os = require("os");

const NOMBRE = "tareas-mcp";
const URL_MCP = "http://localhost:8088/mcp";

function buscarConfig() {
  const local = process.env.LOCALAPPDATA || path.join(os.homedir(), "AppData", "Local");
  const roaming = process.env.APPDATA || path.join(os.homedir(), "AppData", "Roaming");
  const candidatos = [];
  // Instalación desde Microsoft Store: %LOCALAPPDATA%\Claude-Data\<id>\
  const datos = path.join(local, "Claude-Data");
  if (fs.existsSync(datos)) {
    for (const d of fs.readdirSync(datos)) {
      candidatos.push(path.join(datos, d, "claude_desktop_config.json"));
    }
  }
  // Instalación clásica: %APPDATA%\Claude\
  candidatos.push(path.join(roaming, "Claude", "claude_desktop_config.json"));
  return candidatos.find((f) => fs.existsSync(f));
}

const archivo = buscarConfig();
if (!archivo) {
  console.error("No se encontró claude_desktop_config.json. Abra Claude Desktop una vez y reintente.");
  process.exit(1);
}

const config = JSON.parse(fs.readFileSync(archivo, "utf8"));
fs.copyFileSync(archivo, archivo + ".antes-lab-mcp.bak");

config.mcpServers = config.mcpServers || {};
if (process.argv.includes("--quitar")) {
  delete config.mcpServers[NOMBRE];
  console.log(`Se quitó "${NOMBRE}" de ${archivo}`);
} else {
  config.mcpServers[NOMBRE] = {
    command: "cmd",
    args: ["/c", "npx", "-y", "mcp-remote", URL_MCP],
  };
  console.log(`Se agregó "${NOMBRE}" -> ${URL_MCP} en ${archivo}`);
}

fs.writeFileSync(archivo, JSON.stringify(config, null, 2) + "\n");
console.log("mcpServers:", JSON.stringify(config.mcpServers, null, 2));
console.log("Ahora abra Claude Desktop de nuevo.");
