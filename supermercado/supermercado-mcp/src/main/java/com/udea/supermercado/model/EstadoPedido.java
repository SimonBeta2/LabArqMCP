package com.udea.supermercado.model;

public enum EstadoPedido {
    /** Carrito en construcción: se pueden agregar, cambiar o quitar productos. */
    ABIERTO,
    /** Pedido en firme: el stock ya fue descontado y sale a domicilio. */
    CONFIRMADO,
    /** Pedido anulado: si estaba confirmado, el stock se devolvió. */
    CANCELADO
}
