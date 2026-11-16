package com.udea.mcpexample.service;

import com.udea.mcpexample.model.Tarea;
import com.udea.mcpexample.repository.TareaRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

/**
 * CRUD de tareas expuesto como herramientas MCP.
 *
 * Cada método @Tool se publica automáticamente por el servidor MCP:
 *   - nombre      = nombre del método
 *   - descripción = atributo description
 *   - schema      = derivado de los parámetros (incluye @ToolParam)
 *
 * Un cliente MCP (Claude Desktop, Google ADK, MCP Inspector) puede invocar estos
 * métodos cuando el usuario habla en lenguaje natural: "créame una tarea para el
 * lunes", "márcalas todas como completadas", etc.
 */
@Component
public class TareaToolsService {

    /** yyyy-MM-dd estricto: rechaza fechas imposibles como 2026-02-30. */
    private static final DateTimeFormatter ISO_ESTRICTO =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private final TareaRepository repo;

    public TareaToolsService(TareaRepository repo) {
        this.repo = repo;
    }

    // ---------- READ ----------

    @Tool(description = "Devuelve la lista completa de tareas almacenadas.")
    public List<Tarea> listarTareas() {
        return repo.findAll();
    }

    @Tool(description = "Devuelve únicamente las tareas pendientes (completado = false).")
    public List<Tarea> listarPendientes() {
        return repo.findByCompletado(false);
    }

    // Actividad 11
    @Tool(description = "Devuelve únicamente las tareas ya completadas (completado = true).")
    public List<Tarea> listarCompletadas() {
        return repo.findByCompletado(true);
    }

    // Actividad 10
    @Tool(description = "Devuelve el número total de tareas almacenadas, sin importar su estado. "
            + "Úsala cuando el usuario pregunte cuántas tareas tiene, en lugar de listar y contar.")
    public long contarTareas() {
        return repo.count();
    }

    @Tool(description = "Busca tareas cuyo título contiene el fragmento indicado (sin distinguir mayúsculas).")
    public List<Tarea> buscarTareas(
            @ToolParam(description = "Fragmento de texto a buscar en el título") String fragmento) {
        return repo.findByTituloContainingIgnoreCase(fragmento);
    }

    // ---------- CREATE ----------

    @Tool(description = "Crea una nueva tarea con el título y la fecha indicados. Queda como pendiente.")
    @Transactional
    public Tarea crearTarea(
            @ToolParam(description = "Título descriptivo de la tarea") String titulo,
            @ToolParam(description = "Fecha en formato ISO yyyy-MM-dd, por ejemplo 2026-11-20") String fecha) {
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("El título es obligatorio");
        }
        return repo.save(new Tarea(titulo.trim(), validarFecha(fecha), Boolean.FALSE));
    }

    // ---------- UPDATE ----------

    @Tool(description = "Marca como completada la tarea indicada por su id.")
    @Transactional
    public Tarea marcarCompletada(
            @ToolParam(description = "Identificador único de la tarea") Long id) {
        Tarea t = buscarPorId(id);
        t.setCompletado(Boolean.TRUE);
        return repo.save(t);
    }

    @Tool(description = "Actualiza el título o la fecha de una tarea existente. Los parámetros nulos no se modifican.")
    @Transactional
    public Tarea actualizarTarea(
            @ToolParam(description = "Id de la tarea a actualizar") Long id,
            @ToolParam(description = "Nuevo título; null para no cambiar", required = false) String titulo,
            @ToolParam(description = "Nueva fecha en formato ISO yyyy-MM-dd; null para no cambiar", required = false) String fecha) {
        Tarea t = buscarPorId(id);
        if (titulo != null && !titulo.isBlank()) t.setTitulo(titulo.trim());
        if (fecha != null && !fecha.isBlank()) t.setFecha(validarFecha(fecha));
        return repo.save(t);
    }

    // ---------- DELETE ----------

    @Tool(description = "Elimina la tarea con el id indicado. Devuelve un mensaje de confirmación.")
    @Transactional
    public String eliminarTarea(
            @ToolParam(description = "Id de la tarea a eliminar") Long id) {
        if (!repo.existsById(id)) {
            return "La tarea " + id + " no existe.";
        }
        repo.deleteById(id);
        return "Tarea " + id + " eliminada correctamente.";
    }

    // ---------- Apoyo ----------

    private Tarea buscarPorId(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe la tarea " + id));
    }

    /**
     * Actividad 12: la fecha debe venir en formato ISO yyyy-MM-dd.
     * Se valida en el servidor porque no se puede confiar en que el modelo
     * siempre envíe el formato pedido en la descripción de la herramienta.
     */
    private String validarFecha(String fecha) {
        if (fecha == null || fecha.isBlank()) {
            throw new IllegalArgumentException("La fecha es obligatoria y debe tener formato yyyy-MM-dd");
        }
        String limpia = fecha.trim();
        try {
            return LocalDate.parse(limpia, ISO_ESTRICTO).toString();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Fecha inválida '" + limpia + "': use el formato ISO yyyy-MM-dd, por ejemplo 2026-11-20");
        }
    }
}
