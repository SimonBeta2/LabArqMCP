package com.udea.mcpexample.service;

import com.udea.mcpexample.model.Tarea;
import com.udea.mcpexample.repository.TareaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Actividad 15: prueba automatizada de la lógica de negocio y del repositorio.
 * Corre contra H2 en memoria (ver src/test/resources/application.properties).
 */
@SpringBootTest
class TareaToolsServiceTest {

    @Autowired
    private TareaToolsService tools;

    @Autowired
    private TareaRepository repo;

    @BeforeEach
    void datosIniciales() {
        repo.deleteAll();
        repo.save(new Tarea("Preparar charla MCP", "2026-04-20", false));
        repo.save(new Tarea("Revisar slides", "2026-04-21", false));
        repo.save(new Tarea("Probar demo en vivo", "2026-04-22", true));
    }

    @Test
    void listarYContar() {
        assertThat(tools.listarTareas()).hasSize(3);
        assertThat(tools.contarTareas()).isEqualTo(3);
    }

    @Test
    void pendientesYCompletadasSonComplementarias() {
        List<Tarea> pendientes = tools.listarPendientes();
        List<Tarea> completadas = tools.listarCompletadas();

        assertThat(pendientes).hasSize(2).allMatch(t -> !t.getCompletado());
        assertThat(completadas).hasSize(1).allMatch(Tarea::getCompletado);
        assertThat(pendientes.size() + completadas.size()).isEqualTo(tools.contarTareas());
    }

    @Test
    void buscarIgnoraMayusculas() {
        assertThat(tools.buscarTareas("SLIDES"))
                .extracting(Tarea::getTitulo)
                .containsExactly("Revisar slides");
    }

    @Test
    void crearTareaQuedaPendiente() {
        Tarea creada = tools.crearTarea("  Preparar laboratorio MCP  ", "2026-11-20");

        assertThat(creada.getId()).isNotNull();
        assertThat(creada.getTitulo()).isEqualTo("Preparar laboratorio MCP");
        assertThat(creada.getFecha()).isEqualTo("2026-11-20");
        assertThat(creada.getCompletado()).isFalse();
        assertThat(tools.contarTareas()).isEqualTo(4);
    }

    @Test
    void crearTareaRechazaTituloVacio() {
        assertThatThrownBy(() -> tools.crearTarea("   ", "2026-11-20"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void crearTareaRechazaFechaQueNoEsIso() {
        assertThatThrownBy(() -> tools.crearTarea("Tarea", "20/11/2026"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yyyy-MM-dd");
        assertThatThrownBy(() -> tools.crearTarea("Tarea", "2026-02-30"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.crearTarea("Tarea", "el lunes"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(tools.contarTareas()).isEqualTo(3);
    }

    @Test
    void marcarCompletada() {
        Long id = tools.listarPendientes().get(0).getId();

        assertThat(tools.marcarCompletada(id).getCompletado()).isTrue();
        assertThat(tools.listarPendientes()).hasSize(1);
    }

    @Test
    void marcarCompletadaFallaSiNoExiste() {
        assertThatThrownBy(() -> tools.marcarCompletada(9999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("9999");
    }

    @Test
    void actualizarSoloCambiaLoQueNoEsNulo() {
        Tarea original = tools.buscarTareas("slides").get(0);

        Tarea soloTitulo = tools.actualizarTarea(original.getId(), "Revisar diapositivas", null);
        assertThat(soloTitulo.getTitulo()).isEqualTo("Revisar diapositivas");
        assertThat(soloTitulo.getFecha()).isEqualTo("2026-04-21");

        Tarea soloFecha = tools.actualizarTarea(original.getId(), null, "2026-05-01");
        assertThat(soloFecha.getTitulo()).isEqualTo("Revisar diapositivas");
        assertThat(soloFecha.getFecha()).isEqualTo("2026-05-01");

        assertThatThrownBy(() -> tools.actualizarTarea(original.getId(), null, "mañana"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eliminarTarea() {
        Long id = tools.listarTareas().get(0).getId();

        assertThat(tools.eliminarTarea(id)).contains("eliminada");
        assertThat(tools.eliminarTarea(id)).contains("no existe");
        assertThat(tools.contarTareas()).isEqualTo(2);
    }
}
