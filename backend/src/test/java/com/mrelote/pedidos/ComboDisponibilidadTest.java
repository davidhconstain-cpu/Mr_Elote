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

/**
 * RN-004 aplicado a combos (sección 11 v1.1): un componente fijo agotado
 * agota el combo completo; se recalcula contra la vista SQL
 * combo_disponibilidad cada vez que cambia producto.disponible, no solo al
 * crear el combo.
 */
class ComboDisponibilidadTest extends IntegrationTestBase {

    @Test
    void comboSeAgotaCuandoUnComponenteFijoSeAgotaYSeRecuperaAlVolver() {
        String token = login(crearAdmin("admincombo@mrelote.com").getEmail(), "password123");
        Long categoriaId = crearCategoria(token, "Comida");
        Long productoA = crearProducto(token, categoriaId, "COMBO-A", "10000");
        Long productoB = crearProducto(token, categoriaId, "COMBO-B", "8000");

        ResponseEntity<ComboResponse> combo = rest.exchange(
                baseUrl() + "/combos", HttpMethod.POST,
                conToken(token, new ComboRequest("Combo Test", "desc", new BigDecimal("15000"), true)),
                ComboResponse.class);
        Long comboId = combo.getBody().id();

        List<ComboComponenteRequest> componentes = List.of(
                new ComboComponenteRequest(productoA, null, 1, false),
                new ComboComponenteRequest(productoB, null, 1, false));
        rest.exchange(baseUrl() + "/combos/" + comboId + "/componentes", HttpMethod.PUT,
                conToken(token, componentes), ComboResponse.class);

        assertThat(verCombo(comboId).disponible()).isTrue();

        // Se agota un componente fijo -> el combo se agota.
        rest.exchange(baseUrl() + "/productos/" + productoA + "/disponibilidad", HttpMethod.PATCH,
                conToken(token, new DisponibilidadRequest(false)), ProductoResponse.class);
        assertThat(verCombo(comboId).disponible()).isFalse();

        // Vuelve a estar disponible -> el combo también.
        rest.exchange(baseUrl() + "/productos/" + productoA + "/disponibilidad", HttpMethod.PATCH,
                conToken(token, new DisponibilidadRequest(true)), ProductoResponse.class);
        assertThat(verCombo(comboId).disponible()).isTrue();
    }

    @Test
    void comboConGrupoAlternativoSoloSeAgotaSiTodasLasAlternativasEstanAgotadas() {
        String token = login(crearAdmin("admincombo2@mrelote.com").getEmail(), "password123");
        Long categoriaId = crearCategoria(token, "Comida");
        Long bebidaA = crearProducto(token, categoriaId, "BEBIDA-A", "3000");
        Long bebidaB = crearProducto(token, categoriaId, "BEBIDA-B", "3000");

        ResponseEntity<ComboResponse> combo = rest.exchange(
                baseUrl() + "/combos", HttpMethod.POST,
                conToken(token, new ComboRequest("Combo Bebida", null, new BigDecimal("12000"), true)),
                ComboResponse.class);
        Long comboId = combo.getBody().id();

        List<ComboComponenteRequest> componentes = List.of(
                new ComboComponenteRequest(bebidaA, "bebida", 1, false),
                new ComboComponenteRequest(bebidaB, "bebida", 1, false));
        rest.exchange(baseUrl() + "/combos/" + comboId + "/componentes", HttpMethod.PUT,
                conToken(token, componentes), ComboResponse.class);

        rest.exchange(baseUrl() + "/productos/" + bebidaA + "/disponibilidad", HttpMethod.PATCH,
                conToken(token, new DisponibilidadRequest(false)), ProductoResponse.class);
        // Todavía queda bebidaB disponible en el mismo grupo -> el combo sigue disponible.
        assertThat(verCombo(comboId).disponible()).isTrue();

        rest.exchange(baseUrl() + "/productos/" + bebidaB + "/disponibilidad", HttpMethod.PATCH,
                conToken(token, new DisponibilidadRequest(false)), ProductoResponse.class);
        // Ahora las dos alternativas del grupo están agotadas -> el combo se agota.
        assertThat(verCombo(comboId).disponible()).isFalse();
    }

    private ComboResponse verCombo(Long comboId) {
        return rest.getForEntity(baseUrl() + "/combos/" + comboId, ComboResponse.class).getBody();
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
