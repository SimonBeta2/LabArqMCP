package com.udea.supermercado.config;

import com.udea.supermercado.model.Producto;
import com.udea.supermercado.repository.ProductoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.util.List;

/** Carga el catálogo de ejemplo la primera vez (solo si la tabla producto está vacía). */
@Configuration
@Profile("!test")
public class DatosIniciales {

    @Bean
    CommandLineRunner cargarCatalogo(ProductoRepository repo) {
        return args -> {
            if (repo.count() > 0) return;
            repo.saveAll(List.of(
                    p("Leche entera", "Lácteos", "bolsa 1 L", 4200, 40),
                    p("Leche deslactosada", "Lácteos", "bolsa 1 L", 4800, 30),
                    p("Queso campesino", "Lácteos", "bloque 500 g", 13500, 15),
                    p("Yogur de fresa", "Lácteos", "botella 1 L", 9800, 20),
                    p("Huevos AA", "Lácteos", "cubeta x30", 18900, 25),
                    p("Arroz blanco", "Despensa", "bolsa 1 kg", 4600, 60),
                    p("Frijol cargamanto", "Despensa", "bolsa 500 g", 6900, 35),
                    p("Aceite vegetal", "Despensa", "botella 1 L", 12900, 30),
                    p("Azúcar blanca", "Despensa", "bolsa 1 kg", 4900, 40),
                    p("Café molido", "Despensa", "bolsa 500 g", 16500, 25),
                    p("Pasta espagueti", "Despensa", "paquete 500 g", 3800, 45),
                    p("Arepas de maíz", "Panadería", "paquete x5", 4500, 30),
                    p("Pan tajado", "Panadería", "bolsa 500 g", 6800, 20),
                    p("Banano", "Frutas y verduras", "kg", 3200, 50),
                    p("Tomate chonto", "Frutas y verduras", "kg", 4800, 40),
                    p("Papa pastusa", "Frutas y verduras", "kg", 3500, 80),
                    p("Cebolla cabezona", "Frutas y verduras", "kg", 4200, 35),
                    p("Aguacate hass", "Frutas y verduras", "unidad", 3900, 3),
                    p("Pechuga de pollo", "Carnes", "bandeja 1 kg", 19800, 18),
                    p("Carne molida de res", "Carnes", "bandeja 500 g", 14500, 15),
                    p("Gaseosa cola", "Bebidas", "botella 1.5 L", 5900, 40),
                    p("Agua sin gas", "Bebidas", "botella 600 mL", 2200, 60),
                    p("Jabón en barra", "Aseo", "unidad 300 g", 3600, 30),
                    p("Detergente en polvo", "Aseo", "bolsa 1 kg", 11900, 22),
                    p("Papel higiénico", "Aseo", "paquete x12", 21900, 20)
            ));
        };
    }

    private static Producto p(String nombre, String categoria, String presentacion, int precio, int stock) {
        return new Producto(nombre, categoria, presentacion, BigDecimal.valueOf(precio).setScale(2), stock);
    }
}
