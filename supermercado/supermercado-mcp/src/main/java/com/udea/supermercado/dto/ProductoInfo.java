package com.udea.supermercado.dto;

import com.udea.supermercado.model.Producto;

import java.math.BigDecimal;

/** Lo que el modelo ve de un producto del catálogo. */
public record ProductoInfo(Long id, String nombre, String categoria, String presentacion,
                           BigDecimal precio, Integer stock) {

    public static ProductoInfo de(Producto p) {
        return new ProductoInfo(p.getId(), p.getNombre(), p.getCategoria(), p.getPresentacion(),
                p.getPrecio(), p.getStock());
    }
}
