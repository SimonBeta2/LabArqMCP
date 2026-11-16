package com.udea.supermercado.dto;

import com.udea.supermercado.model.ItemPedido;
import com.udea.supermercado.model.Pedido;

import java.math.BigDecimal;
import java.util.List;

/**
 * Vista de un pedido para el modelo. Se devuelve un record en lugar de la entidad JPA
 * para no exponer relaciones perezosas ni ciclos pedido -> item -> pedido al serializar.
 */
public record PedidoInfo(Long id, String estado, String cliente, String direccion, String telefono,
                         List<Item> items, BigDecimal subtotal, BigDecimal domicilio, BigDecimal total,
                         String creado, String confirmado) {

    public record Item(Long productoId, String producto, String presentacion, Integer cantidad,
                       BigDecimal precioUnitario, BigDecimal subtotal) {

        static Item de(ItemPedido i) {
            return new Item(i.getProducto().getId(), i.getProducto().getNombre(),
                    i.getProducto().getPresentacion(), i.getCantidad(), i.getPrecioUnitario(), i.getSubtotal());
        }
    }

    public static PedidoInfo de(Pedido p, BigDecimal subtotal, BigDecimal domicilio) {
        return new PedidoInfo(p.getId(), p.getEstado().name(), p.getCliente(), p.getDireccion(), p.getTelefono(),
                p.getItems().stream().map(Item::de).toList(),
                subtotal, domicilio, subtotal.add(domicilio),
                p.getCreado().toString(),
                p.getConfirmado() == null ? null : p.getConfirmado().toString());
    }
}
