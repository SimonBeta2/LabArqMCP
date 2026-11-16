package com.udea.supermercado.repository;

import com.udea.supermercado.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    List<Pedido> findByTelefonoOrderByCreadoDesc(String telefono);
}
