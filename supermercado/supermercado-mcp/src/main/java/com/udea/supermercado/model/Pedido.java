package com.udea.supermercado.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedido")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String cliente;

    @Column(nullable = false)
    private String direccion;

    @Column(nullable = false)
    private String telefono;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPedido estado = EstadoPedido.ABIERTO;

    @Column(nullable = false)
    private LocalDateTime creado = LocalDateTime.now();

    private LocalDateTime confirmado;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ItemPedido> items = new ArrayList<>();

    public Pedido() {}

    public Pedido(String cliente, String direccion, String telefono) {
        this.cliente = cliente;
        this.direccion = direccion;
        this.telefono = telefono;
    }

    public Long getId() { return id; }
    public String getCliente() { return cliente; }
    public String getDireccion() { return direccion; }
    public String getTelefono() { return telefono; }
    public EstadoPedido getEstado() { return estado; }
    public void setEstado(EstadoPedido estado) { this.estado = estado; }
    public LocalDateTime getCreado() { return creado; }
    public LocalDateTime getConfirmado() { return confirmado; }
    public void setConfirmado(LocalDateTime confirmado) { this.confirmado = confirmado; }
    public List<ItemPedido> getItems() { return items; }
}
