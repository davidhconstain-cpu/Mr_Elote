package com.mrelote.pedidos;

import com.mrelote.pedidos.dto.request.*;
import com.mrelote.pedidos.dto.response.*;
import com.mrelote.pedidos.security.RoleNames;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ciclo de vida completo de un pedido "recoger" anónimo, encadenando los
 * cuatro roles que realmente lo mueven — el mismo flujo que Ronda 1/8
 * verificaron manualmente con curl/Playwright, ahora como test repetible.
 */
class PedidoRecogerFlowTest extends IntegrationTestBase {

    @Test
    void pedidoRecogerPasaPorTodasLasTransicionesYQuedaHistorialCompleto() {
        String tokenAdmin = login(crearAdmin("admin@mrelote.com").getEmail(), "password123");
        String tokenCaja = login(crearUsuarioStaff("Caja Test", "caja@mrelote.com", RoleNames.CAJA).getEmail(), "password123");
        String tokenCocina = login(crearUsuarioStaff("Cocina Test", "cocina@mrelote.com", RoleNames.COCINA).getEmail(), "password123");

        Long categoriaId = crearCategoria(tokenAdmin, "Comida");
        Long productoId = crearProducto(tokenAdmin, categoriaId, "REC-1", "20000");

        var itemRequest = new ItemPedidoRequest(productoId, null, 2, null, null, null);
        var pedidoRequest = new CrearPedidoRequest("recoger", null, null, List.of(itemRequest), "sin cebolla");
        ResponseEntity<PedidoResponse> creado = rest.postForEntity(baseUrl() + "/pedidos", pedidoRequest, PedidoResponse.class);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PedidoResponse pedido = creado.getBody();
        assertThat(pedido.estado()).isEqualTo("pago_pendiente");
        assertThat(pedido.total()).isEqualByComparingTo(new BigDecimal("40000"));
        Long pedidoId = pedido.id();

        transicion(tokenCaja, pedidoId, "confirmar-pago", "pago_validado");
        transicion(tokenCocina, pedidoId, "iniciar-preparacion", "en_preparacion");
        transicion(tokenCocina, pedidoId, "marcar-listo", "listo");
        transicion(tokenCaja, pedidoId, "recoger", "recogido");

        ResponseEntity<HistorialPedidoResponse[]> historial = rest.exchange(
                baseUrl() + "/pedidos/" + pedidoId + "/historial", HttpMethod.GET,
                conToken(tokenAdmin), HistorialPedidoResponse[].class);
        assertThat(historial.getBody()).hasSize(4);

        // Un pedido en estado final no se puede anular.
        ResponseEntity<String> anular = rest.exchange(
                baseUrl() + "/pedidos/" + pedidoId + "/anular", HttpMethod.POST,
                conToken(tokenAdmin, new AccionPedidoRequest("motivo cualquiera")), String.class);
        assertThat(anular.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cocinaNoPuedeConfirmarPagoDeUnPedido() {
        String tokenCocina = login(crearUsuarioStaff("Cocina Test", "cocina2@mrelote.com", RoleNames.COCINA).getEmail(), "password123");
        String tokenAdmin = login(crearAdmin("admin2@mrelote.com").getEmail(), "password123");
        Long categoriaId = crearCategoria(tokenAdmin, "Comida");
        Long productoId = crearProducto(tokenAdmin, categoriaId, "REC-2", "10000");

        var pedidoRequest = new CrearPedidoRequest("recoger", null, null,
                List.of(new ItemPedidoRequest(productoId, null, 1, null, null, null)), null);
        Long pedidoId = rest.postForEntity(baseUrl() + "/pedidos", pedidoRequest, PedidoResponse.class).getBody().id();

        ResponseEntity<String> respuesta = rest.exchange(
                baseUrl() + "/pedidos/" + pedidoId + "/confirmar-pago", HttpMethod.POST,
                conToken(tokenCocina), String.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private void transicion(String token, Long pedidoId, String accion, String estadoEsperado) {
        ResponseEntity<PedidoResponse> respuesta = rest.exchange(
                baseUrl() + "/pedidos/" + pedidoId + "/" + accion, HttpMethod.POST,
                conToken(token), PedidoResponse.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody().estado()).isEqualTo(estadoEsperado);
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
