package com.mrelote.pedidos;

import com.mrelote.pedidos.dto.request.*;
import com.mrelote.pedidos.dto.response.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PedidoDomicilioTest extends IntegrationTestBase {

    @Test
    void totalDelPedidoIncluyeLaTarifaDeLaZonaDeLaDireccion() {
        String tokenAdmin = login(registrarStaffAdmin(), "password123");
        Long categoriaId = crearCategoria(tokenAdmin, "Comida");
        Long productoId = crearProducto(tokenAdmin, categoriaId, "DOM-1", "30000");

        ResponseEntity<ZonaDomicilioResponse> zona = rest.exchange(
                baseUrl() + "/zonas-domicilio", HttpMethod.POST,
                conToken(tokenAdmin, new ZonaDomicilioRequest("Zona Test", true, new BigDecimal("6000"), 25)),
                ZonaDomicilioResponse.class);
        assertThat(zona.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Cliente propio (no admin) para poder crear dirección y pedido a domicilio.
        var registro = new RegistroClienteRequest("Cliente Domicilio", "clientedom@example.com", null, "password123");
        LoginResponse cliente = rest.postForEntity(baseUrl() + "/auth/registro", registro, LoginResponse.class).getBody();
        String tokenCliente = cliente.accessToken();

        ResponseEntity<DireccionResponse> direccion = rest.exchange(
                baseUrl() + "/clientes/me/direcciones", HttpMethod.POST,
                conToken(tokenCliente, new DireccionRequest("Casa", "Calle falsa 123", zona.getBody().id(), null, true)),
                DireccionResponse.class);
        assertThat(direccion.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var pedidoRequest = new CrearPedidoRequest("domicilio", null, direccion.getBody().id(),
                List.of(new ItemPedidoRequest(productoId, null, 1, null, null, null)), null);
        ResponseEntity<PedidoResponse> pedido = rest.exchange(
                baseUrl() + "/pedidos", HttpMethod.POST, conToken(tokenCliente, pedidoRequest), PedidoResponse.class);

        assertThat(pedido.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(pedido.getBody().subtotal()).isEqualByComparingTo(new BigDecimal("30000"));
        assertThat(pedido.getBody().domicilio()).isEqualByComparingTo(new BigDecimal("6000"));
        assertThat(pedido.getBody().total()).isEqualByComparingTo(new BigDecimal("36000"));
        assertThat(pedido.getBody().canal()).isEqualTo("web");
    }

    @Test
    void pedidoDomicilioSinClienteAutenticadoEsRechazado() {
        var pedidoRequest = new CrearPedidoRequest("domicilio", null, 1L,
                List.of(new ItemPedidoRequest(1L, null, 1, null, null, null)), null);
        ResponseEntity<String> respuesta = rest.postForEntity(baseUrl() + "/pedidos", pedidoRequest, String.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private String registrarStaffAdmin() {
        crearAdmin("admindom@mrelote.com");
        return "admindom@mrelote.com";
    }

    private String login(String email, String password) {
        var respuesta = rest.postForEntity(baseUrl() + "/auth/login", new LoginRequest(email, password), LoginResponse.class);
        return respuesta.getBody().accessToken();
    }

    private Long crearCategoria(String token, String nombre) {
        var respuesta = rest.exchange(baseUrl() + "/categorias", HttpMethod.POST,
                conToken(token, new CategoriaRequest(nombre, 1, true)), CategoriaResponse.class);
        return respuesta.getBody().id();
    }

    private Long crearProducto(String token, Long categoriaId, String codigo, String precio) {
        var request = new ProductoRequest(categoriaId, codigo, codigo, null, new BigDecimal(precio), null, true, true);
        var respuesta = rest.exchange(baseUrl() + "/productos", HttpMethod.POST,
                conToken(token, request), ProductoResponse.class);
        return respuesta.getBody().id();
    }
}
