package com.mrelote.pedidos;

import com.mrelote.pedidos.dto.request.CategoriaRequest;
import com.mrelote.pedidos.dto.request.DisponibilidadRequest;
import com.mrelote.pedidos.dto.request.LoginRequest;
import com.mrelote.pedidos.dto.request.ProductoRequest;
import com.mrelote.pedidos.dto.response.CategoriaResponse;
import com.mrelote.pedidos.dto.response.LoginResponse;
import com.mrelote.pedidos.dto.response.ProductoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogoTest extends IntegrationTestBase {

    private String tokenAdmin() {
        crearAdmin("admin@mrelote.com");
        var login = rest.postForEntity(baseUrl() + "/auth/login",
                new LoginRequest("admin@mrelote.com", "password123"), LoginResponse.class);
        return login.getBody().accessToken();
    }

    @Test
    void crearCategoriaYProductoQuedanVisiblesPublicamente() {
        String token = tokenAdmin();

        var categoriaRequest = new CategoriaRequest("Comida", 1, true);
        ResponseEntity<CategoriaResponse> categoria = rest.exchange(
                baseUrl() + "/categorias", org.springframework.http.HttpMethod.POST,
                conToken(token, categoriaRequest), CategoriaResponse.class);
        assertThat(categoria.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var productoRequest = new ProductoRequest(
                categoria.getBody().id(), "TEST-1", "Producto de prueba", "Descripción", new BigDecimal("15000"),
                2, true, true);
        ResponseEntity<ProductoResponse> producto = rest.exchange(
                baseUrl() + "/productos", org.springframework.http.HttpMethod.POST,
                conToken(token, productoRequest), ProductoResponse.class);
        assertThat(producto.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(producto.getBody().disponible()).isTrue();

        // Público, sin token: el catálogo debe verse igual.
        ResponseEntity<String> listaPublica = rest.getForEntity(baseUrl() + "/productos", String.class);
        assertThat(listaPublica.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listaPublica.getBody()).contains("TEST-1");
    }

    @Test
    void marcarProductoAgotadoLoDejaVisiblePeroNoDisponible() {
        String token = tokenAdmin();
        Long categoriaId = crearCategoria(token, "Comida");
        Long productoId = crearProducto(token, categoriaId, "TEST-AGOTADO", "5000");

        ResponseEntity<ProductoResponse> respuesta = rest.exchange(
                baseUrl() + "/productos/" + productoId + "/disponibilidad", org.springframework.http.HttpMethod.PATCH,
                conToken(token, new DisponibilidadRequest(false)), ProductoResponse.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody().disponible()).isFalse();
        assertThat(respuesta.getBody().activo()).isTrue();

        // RN-004: sigue apareciendo en el listado público, no se oculta.
        ResponseEntity<String> listaPublica = rest.getForEntity(baseUrl() + "/productos", String.class);
        assertThat(listaPublica.getBody()).contains("TEST-AGOTADO");
    }

    @Test
    void crearProductoSinAutenticacionEsRechazado() {
        var productoRequest = new ProductoRequest(1L, "SIN-AUTH", "x", null, new BigDecimal("1000"), null, true, true);
        ResponseEntity<String> respuesta = rest.postForEntity(baseUrl() + "/productos", productoRequest, String.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private Long crearCategoria(String token, String nombre) {
        ResponseEntity<CategoriaResponse> categoria = rest.exchange(
                baseUrl() + "/categorias", org.springframework.http.HttpMethod.POST,
                conToken(token, new CategoriaRequest(nombre, 1, true)), CategoriaResponse.class);
        return categoria.getBody().id();
    }

    private Long crearProducto(String token, Long categoriaId, String codigo, String precio) {
        var productoRequest = new ProductoRequest(categoriaId, codigo, codigo, null, new BigDecimal(precio), null, true, true);
        ResponseEntity<ProductoResponse> producto = rest.exchange(
                baseUrl() + "/productos", org.springframework.http.HttpMethod.POST,
                conToken(token, productoRequest), ProductoResponse.class);
        return producto.getBody().id();
    }
}
