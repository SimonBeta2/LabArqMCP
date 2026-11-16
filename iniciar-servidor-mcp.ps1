# Arranca el servidor MCP de tareas (puerto 8088) contra PostgreSQL.
# Uso:  .\iniciar-servidor-mcp.ps1
# Pide la clave de PostgreSQL solo si no está ya en la variable PWD_UDEA.

$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"   # el proyecto pide Java 21 o superior
if (-not $env:DB_USER)  { $env:DB_USER = "betansosa" }
if (-not $env:PWD_UDEA) {
    $segura = Read-Host "Clave de PostgreSQL para $($env:DB_USER)" -AsSecureString
    $env:PWD_UDEA = [System.Net.NetworkCredential]::new("", $segura).Password
}

Set-Location "$PSScriptRoot\mcpexample"
.\mvnw.cmd spring-boot:run
