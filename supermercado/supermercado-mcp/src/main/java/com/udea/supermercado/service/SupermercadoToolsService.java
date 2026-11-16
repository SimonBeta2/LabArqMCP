package com.udea.supermercado.service;

import com.udea.supermercado.dto.PedidoInfo;
import com.udea.supermercado.dto.ProductoInfo;
import com.udea.supermercado.model.EstadoPedido;
import com.udea.supermercado.model.ItemPedido;
import com.udea.supermercado.model.Pedido;
import com.udea.supermercado.model.Producto;
import com.udea.supermercado.repository.PedidoRepository;
import com.udea.supermercado.repository.ProductoRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Pedidos de supermercado a domicilio expuestos como herramientas MCP.
 *
 * Flujo que sigue el chatbot:
 *   1. consulta el catálogo (listarCategorias / listarProductos / buscarProductos)
 *   2. crearPedido            -> pedido ABIERTO (carrito)
 *   3. agregarProducto / cambiarCantidad
 *   4. verPedido              -> resumen con subtotal, domicilio y total
 *   5. confirmarPedido        -> descuenta stock, pedido CONFIRMADO
 *   (cancelarPedido devuelve el stock si ya estaba confirmado)
 *
 * Todas las reglas de negocio se validan aquí, en el servidor: el modelo solo propone
 * argumentos y no se puede confiar en que siempre respete las instrucciones.
 */
@Component
public class SupermercadoToolsService {

    static final BigDecimal COSTO_DOMICILIO = new BigDecimal("5000.00");
    static final BigDecimal DOMICILIO_GRATIS_DESDE = new BigDecimal("80000.00");
    static final int CANTIDAD_MAXIMA_POR_PRODUCTO = 50;

    private final ProductoRepository productos;
    private final PedidoRepository pedidos;

    public SupermercadoToolsService(ProductoRepository productos, PedidoRepository pedidos) {
        this.productos = productos;
        this.pedidos = pedidos;
    }

    // ---------- Catálogo ----------

    @Tool(description = "Lista las categorías de productos disponibles en el supermercado "
            + "(por ejemplo Lácteos, Frutas y verduras, Aseo).")
    @Transactional(readOnly = true)
    public List<String> listarCategorias() {
        return productos.findCategorias();
    }

    @Tool(description = "Lista los productos del catálogo con id, precio en pesos colombianos y stock. "
            + "Si se indica una categoría devuelve solo los de esa categoría; sin categoría devuelve todos.")
    @Transactional(readOnly = true)
    public List<ProductoInfo> listarProductos(
            @ToolParam(description = "Nombre exacto de la categoría; omitir para ver todo el catálogo",
                    required = false) String categoria) {
        List<Producto> lista = (categoria == null || categoria.isBlank())
                ? productos.findAllByOrderByCategoriaAscNombreAsc()
                : productos.findByCategoriaIgnoreCaseOrderByNombre(categoria.trim());
        return lista.stream().map(ProductoInfo::de).toList();
    }

    @Tool(description = "Busca productos cuyo nombre contiene el texto indicado (sin distinguir mayúsculas). "
            + "Úsala para encontrar el id de un producto que el cliente menciona, por ejemplo 'leche' o 'arroz'.")
    @Transactional(readOnly = true)
    public List<ProductoInfo> buscarProductos(
            @ToolParam(description = "Texto a buscar en el nombre del producto") String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("Indique un texto para buscar");
        }
        return productos.findByNombreContainingIgnoreCaseOrderByNombre(texto.trim())
                .stream().map(ProductoInfo::de).toList();
    }

    // ---------- Pedido ----------

    @Tool(description = "Crea un pedido a domicilio vacío (estado ABIERTO) para un cliente y devuelve su id. "
            + "Se necesita antes de agregar productos. Pida al cliente nombre, dirección y teléfono; no los invente.")
    @Transactional
    public PedidoInfo crearPedido(
            @ToolParam(description = "Nombre completo del cliente") String cliente,
            @ToolParam(description = "Dirección de entrega del domicilio") String direccion,
            @ToolParam(description = "Teléfono de contacto, solo dígitos (7 a 10)") String telefono) {
        String tel = telefono == null ? "" : telefono.replaceAll("[\\s-]", "");
        if (esVacio(cliente)) throw new IllegalArgumentException("El nombre del cliente es obligatorio");
        if (esVacio(direccion)) throw new IllegalArgumentException("La dirección de entrega es obligatoria");
        if (!tel.matches("\\d{7,10}")) {
            throw new IllegalArgumentException("El teléfono debe tener entre 7 y 10 dígitos");
        }
        return vista(pedidos.save(new Pedido(cliente.trim(), direccion.trim(), tel)));
    }

    @Tool(description = "Agrega un producto a un pedido ABIERTO. Si el producto ya está en el pedido, suma la cantidad. "
            + "Falla si no hay stock suficiente. Devuelve el pedido actualizado con sus totales.")
    @Transactional
    public PedidoInfo agregarProducto(
            @ToolParam(description = "Id del pedido") Long pedidoId,
            @ToolParam(description = "Id del producto (obtenido de buscarProductos o listarProductos)") Long productoId,
            @ToolParam(description = "Cantidad de unidades a agregar, mayor que cero") Integer cantidad) {
        Pedido pedido = pedidoAbierto(pedidoId);
        Producto producto = producto(productoId);
        if (cantidad == null || cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }
        Optional<ItemPedido> existente = item(pedido, productoId);
        int nueva = existente.map(ItemPedido::getCantidad).orElse(0) + cantidad;
        validarCantidad(producto, nueva);
        if (existente.isPresent()) {
            existente.get().setCantidad(nueva);
        } else {
            pedido.getItems().add(new ItemPedido(pedido, producto, nueva));
        }
        return vista(pedidos.save(pedido));
    }

    @Tool(description = "Fija la cantidad de un producto que ya está en un pedido ABIERTO. "
            + "Con cantidad 0 el producto se quita del pedido. Devuelve el pedido actualizado.")
    @Transactional
    public PedidoInfo cambiarCantidad(
            @ToolParam(description = "Id del pedido") Long pedidoId,
            @ToolParam(description = "Id del producto que ya está en el pedido") Long productoId,
            @ToolParam(description = "Nueva cantidad; 0 para quitar el producto") Integer cantidad) {
        Pedido pedido = pedidoAbierto(pedidoId);
        ItemPedido it = item(pedido, productoId).orElseThrow(() ->
                new IllegalArgumentException("El producto " + productoId + " no está en el pedido " + pedidoId));
        if (cantidad == null || cantidad < 0) {
            throw new IllegalArgumentException("La cantidad no puede ser negativa");
        }
        if (cantidad == 0) {
            pedido.getItems().remove(it);
        } else {
            validarCantidad(it.getProducto(), cantidad);
            it.setCantidad(cantidad);
        }
        return vista(pedidos.save(pedido));
    }

    @Tool(description = "Muestra un pedido: estado, datos de entrega, productos, subtotal, costo de domicilio y total. "
            + "Úsala para presentar el resumen al cliente antes de confirmar.")
    @Transactional(readOnly = true)
    public PedidoInfo verPedido(@ToolParam(description = "Id del pedido") Long pedidoId) {
        return vista(pedido(pedidoId));
    }

    @Tool(description = "Confirma un pedido ABIERTO: verifica stock, lo descuenta del inventario y deja el pedido CONFIRMADO. "
            + "Es una acción en firme: llámela solo después de mostrar el resumen y de que el cliente acepte explícitamente.")
    @Transactional
    public PedidoInfo confirmarPedido(@ToolParam(description = "Id del pedido a confirmar") Long pedidoId) {
        Pedido pedido = pedidoAbierto(pedidoId);
        if (pedido.getItems().isEmpty()) {
            throw new IllegalStateException("El pedido " + pedidoId + " no tiene productos; agregue al menos uno");
        }
        // Se valida todo antes de descontar para no dejar el inventario a medias.
        for (ItemPedido it : pedido.getItems()) {
            validarCantidad(it.getProducto(), it.getCantidad());
        }
        for (ItemPedido it : pedido.getItems()) {
            Producto p = it.getProducto();
            p.setStock(p.getStock() - it.getCantidad());
        }
        pedido.setEstado(EstadoPedido.CONFIRMADO);
        pedido.setConfirmado(LocalDateTime.now());
        return vista(pedidos.save(pedido));
    }

    @Tool(description = "Cancela un pedido. Si ya estaba CONFIRMADO, devuelve los productos al inventario. "
            + "Confirme con el cliente antes de cancelar.")
    @Transactional
    public PedidoInfo cancelarPedido(@ToolParam(description = "Id del pedido a cancelar") Long pedidoId) {
        Pedido pedido = pedido(pedidoId);
        if (pedido.getEstado() == EstadoPedido.CANCELADO) {
            throw new IllegalStateException("El pedido " + pedidoId + " ya estaba cancelado");
        }
        if (pedido.getEstado() == EstadoPedido.CONFIRMADO) {
            for (ItemPedido it : pedido.getItems()) {
                Producto p = it.getProducto();
                p.setStock(p.getStock() + it.getCantidad());
            }
        }
        pedido.setEstado(EstadoPedido.CANCELADO);
        return vista(pedidos.save(pedido));
    }

    @Tool(description = "Lista los pedidos de un cliente a partir de su teléfono, del más reciente al más antiguo.")
    @Transactional(readOnly = true)
    public List<PedidoInfo> listarPedidosCliente(
            @ToolParam(description = "Teléfono con el que se hicieron los pedidos") String telefono) {
        String tel = telefono == null ? "" : telefono.replaceAll("[\\s-]", "");
        return pedidos.findByTelefonoOrderByCreadoDesc(tel).stream().map(this::vista).toList();
    }

    // ---------- Apoyo ----------

    private PedidoInfo vista(Pedido p) {
        BigDecimal subtotal = p.getItems().stream()
                .map(ItemPedido::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return PedidoInfo.de(p, subtotal, domicilio(subtotal));
    }

    /** Domicilio fijo, gratis desde cierto monto; un pedido vacío aún no cobra domicilio. */
    static BigDecimal domicilio(BigDecimal subtotal) {
        if (subtotal.signum() == 0 || subtotal.compareTo(DOMICILIO_GRATIS_DESDE) >= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return COSTO_DOMICILIO;
    }

    private Pedido pedido(Long id) {
        if (id == null) throw new IllegalArgumentException("Indique el id del pedido");
        return pedidos.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe el pedido " + id));
    }

    private Pedido pedidoAbierto(Long id) {
        Pedido p = pedido(id);
        if (p.getEstado() != EstadoPedido.ABIERTO) {
            throw new IllegalStateException("El pedido " + id + " está " + p.getEstado()
                    + " y ya no se puede modificar; cree un pedido nuevo");
        }
        return p;
    }

    private Producto producto(Long id) {
        if (id == null) throw new IllegalArgumentException("Indique el id del producto");
        return productos.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el producto " + id + "; use buscarProductos para obtener el id correcto"));
    }

    private Optional<ItemPedido> item(Pedido pedido, Long productoId) {
        return pedido.getItems().stream()
                .filter(i -> i.getProducto().getId().equals(productoId))
                .findFirst();
    }

    private void validarCantidad(Producto p, int cantidad) {
        if (cantidad > CANTIDAD_MAXIMA_POR_PRODUCTO) {
            throw new IllegalArgumentException("Máximo " + CANTIDAD_MAXIMA_POR_PRODUCTO
                    + " unidades por producto en un pedido");
        }
        if (cantidad > p.getStock()) {
            throw new IllegalStateException("Stock insuficiente de " + p.getNombre()
                    + ": se pidieron " + cantidad + " y hay " + p.getStock());
        }
    }

    private static boolean esVacio(String s) {
        return s == null || s.isBlank();
    }
}
