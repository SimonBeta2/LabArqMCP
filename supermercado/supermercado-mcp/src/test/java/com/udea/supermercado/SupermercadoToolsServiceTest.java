package com.udea.supermercado;

import com.udea.supermercado.dto.PedidoInfo;
import com.udea.supermercado.model.Producto;
import com.udea.supermercado.repository.PedidoRepository;
import com.udea.supermercado.repository.ProductoRepository;
import com.udea.supermercado.service.SupermercadoToolsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Prueba el flujo completo del pedido contra H2 en memoria. */
@SpringBootTest
@ActiveProfiles("test")
class SupermercadoToolsServiceTest {

    @Autowired SupermercadoToolsService tools;
    @Autowired ProductoRepository productos;
    @Autowired PedidoRepository pedidos;

    Long leche, arroz, aguacate;

    @BeforeEach
    void catalogo() {
        pedidos.deleteAll();
        productos.deleteAll();
        leche = productos.save(new Producto("Leche entera", "Lácteos", "bolsa 1 L", new BigDecimal("4200.00"), 40)).getId();
        arroz = productos.save(new Producto("Arroz blanco", "Despensa", "bolsa 1 kg", new BigDecimal("4600.00"), 60)).getId();
        aguacate = productos.save(new Producto("Aguacate hass", "Frutas y verduras", "unidad", new BigDecimal("3900.00"), 3)).getId();
    }

    private PedidoInfo pedidoNuevo() {
        return tools.crearPedido("Ana Pérez", "Calle 10 # 20-30", "300 123 4567");
    }

    @Test
    void catalogoSeConsultaPorCategoriaYPorTexto() {
        assertThat(tools.listarCategorias()).containsExactly("Despensa", "Frutas y verduras", "Lácteos");
        assertThat(tools.listarProductos(null)).hasSize(3);
        assertThat(tools.listarProductos("lácteos")).extracting("nombre").containsExactly("Leche entera");
        assertThat(tools.buscarProductos("ARROZ")).extracting("id").containsExactly(arroz);
    }

    @Test
    void crearPedidoValidaLosDatosDeEntrega() {
        PedidoInfo p = pedidoNuevo();
        assertThat(p.estado()).isEqualTo("ABIERTO");
        assertThat(p.telefono()).isEqualTo("3001234567");
        assertThat(p.total()).isEqualByComparingTo("0");

        assertThatThrownBy(() -> tools.crearPedido(" ", "Calle 1", "3001234567"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.crearPedido("Ana", "Calle 1", "abc"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("teléfono");
    }

    @Test
    void agregarSumaCantidadesYCalculaTotalesConDomicilio() {
        Long id = pedidoNuevo().id();
        tools.agregarProducto(id, leche, 2);
        PedidoInfo p = tools.agregarProducto(id, leche, 1);
        p = tools.agregarProducto(id, arroz, 2);

        assertThat(p.items()).hasSize(2);
        assertThat(p.items().get(0).cantidad()).isEqualTo(3);
        assertThat(p.subtotal()).isEqualByComparingTo("21800");      // 3*4200 + 2*4600
        assertThat(p.domicilio()).isEqualByComparingTo("5000");
        assertThat(p.total()).isEqualByComparingTo("26800");
    }

    @Test
    void domicilioEsGratisDesdeOchentaMil() {
        Long id = pedidoNuevo().id();
        PedidoInfo p = tools.agregarProducto(id, leche, 20);          // 84.000
        assertThat(p.domicilio()).isEqualByComparingTo("0");
        assertThat(p.total()).isEqualByComparingTo("84000");
    }

    @Test
    void noDejaPedirMasQueElStock() {
        Long id = pedidoNuevo().id();
        assertThatThrownBy(() -> tools.agregarProducto(id, aguacate, 4))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Stock insuficiente");
        tools.agregarProducto(id, aguacate, 2);
        assertThatThrownBy(() -> tools.agregarProducto(id, aguacate, 2))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> tools.agregarProducto(id, leche, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.agregarProducto(id, 99999L, 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No existe el producto");
    }

    @Test
    void cambiarCantidadYQuitarConCero() {
        Long id = pedidoNuevo().id();
        tools.agregarProducto(id, leche, 2);
        tools.agregarProducto(id, arroz, 1);

        assertThat(tools.cambiarCantidad(id, leche, 5).items().get(0).cantidad()).isEqualTo(5);
        PedidoInfo p = tools.cambiarCantidad(id, arroz, 0);
        assertThat(p.items()).extracting("producto").containsExactly("Leche entera");
        assertThatThrownBy(() -> tools.cambiarCantidad(id, arroz, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confirmarDescuentaStockYCierraElPedido() {
        Long id = pedidoNuevo().id();
        tools.agregarProducto(id, leche, 3);
        tools.agregarProducto(id, aguacate, 3);

        PedidoInfo p = tools.confirmarPedido(id);

        assertThat(p.estado()).isEqualTo("CONFIRMADO");
        assertThat(p.confirmado()).isNotNull();
        assertThat(productos.findById(leche).orElseThrow().getStock()).isEqualTo(37);
        assertThat(productos.findById(aguacate).orElseThrow().getStock()).isZero();

        assertThatThrownBy(() -> tools.agregarProducto(id, arroz, 1))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("CONFIRMADO");
        assertThatThrownBy(() -> tools.confirmarPedido(id))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void noConfirmaPedidoVacioNiSiElStockSeAgotoMientrasTanto() {
        Long vacio = pedidoNuevo().id();
        assertThatThrownBy(() -> tools.confirmarPedido(vacio))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no tiene productos");

        Long a = pedidoNuevo().id();
        Long b = pedidoNuevo().id();
        tools.agregarProducto(a, aguacate, 2);
        tools.agregarProducto(b, aguacate, 2);
        tools.agregarProducto(b, leche, 1);
        tools.confirmarPedido(a);

        assertThatThrownBy(() -> tools.confirmarPedido(b))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Stock insuficiente");
        // Como falló la validación, no se descontó nada del pedido b
        assertThat(productos.findById(leche).orElseThrow().getStock()).isEqualTo(40);
        assertThat(productos.findById(aguacate).orElseThrow().getStock()).isEqualTo(1);
        assertThat(tools.verPedido(b).estado()).isEqualTo("ABIERTO");
    }

    @Test
    void cancelarDevuelveElStockSiEstabaConfirmado() {
        Long id = pedidoNuevo().id();
        tools.agregarProducto(id, leche, 4);
        tools.confirmarPedido(id);

        assertThat(tools.cancelarPedido(id).estado()).isEqualTo("CANCELADO");
        assertThat(productos.findById(leche).orElseThrow().getStock()).isEqualTo(40);
        assertThatThrownBy(() -> tools.cancelarPedido(id)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void listarPedidosPorTelefono() {
        pedidoNuevo();
        pedidoNuevo();
        tools.crearPedido("Otro", "Cra 1", "6041234567");
        assertThat(tools.listarPedidosCliente("300-123-4567")).hasSize(2);
        assertThat(tools.listarPedidosCliente("0000000")).isEmpty();
    }
}
