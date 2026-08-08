package com.mrelote.pedidos;

import com.mrelote.pedidos.dto.request.LoginRequest;
import com.mrelote.pedidos.dto.request.RegistroClienteRequest;
import com.mrelote.pedidos.dto.response.LoginResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class AuthFlowTest extends IntegrationTestBase {

    @Test
    void registroLoginYRefreshFuncionan() {
        var registro = new RegistroClienteRequest("Ana Cliente", "ana@example.com", "3000000000", "password123");
        ResponseEntity<LoginResponse> respuestaRegistro =
                rest.postForEntity(baseUrl() + "/auth/registro", registro, LoginResponse.class);

        assertThat(respuestaRegistro.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respuestaRegistro.getBody()).isNotNull();
        assertThat(respuestaRegistro.getBody().usuario().rol()).isEqualTo("Cliente");
        assertThat(respuestaRegistro.getBody().accessToken()).isNotBlank();

        var login = new LoginRequest("ana@example.com", "password123");
        ResponseEntity<LoginResponse> respuestaLogin =
                rest.postForEntity(baseUrl() + "/auth/login", login, LoginResponse.class);
        assertThat(respuestaLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuestaLogin.getBody().usuario().email()).isEqualTo("ana@example.com");

        var loginMalo = new LoginRequest("ana@example.com", "password-incorrecta");
        ResponseEntity<String> respuestaLoginMalo =
                rest.postForEntity(baseUrl() + "/auth/login", loginMalo, String.class);
        assertThat(respuestaLoginMalo.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String refreshToken = respuestaLogin.getBody().refreshToken();
        var refreshRequest = conToken(refreshToken);
        ResponseEntity<LoginResponse> respuestaRefresh =
                rest.postForEntity(baseUrl() + "/auth/refresh", refreshRequest, LoginResponse.class);
        assertThat(respuestaRefresh.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuestaRefresh.getBody().accessToken()).isNotBlank();
    }

    @Test
    void noSePuedeRegistrarDosVecesConElMismoEmail() {
        var registro = new RegistroClienteRequest("Ana Cliente", "duplicado@example.com", null, "password123");
        rest.postForEntity(baseUrl() + "/auth/registro", registro, LoginResponse.class);

        ResponseEntity<String> segundaVez =
                rest.postForEntity(baseUrl() + "/auth/registro", registro, String.class);
        assertThat(segundaVez.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void recuperarContrasenaSiempreResponde202SinRevelarSiElEmailExiste() {
        var registro = new RegistroClienteRequest("Con Cuenta", "concuenta@example.com", null, "password123");
        rest.postForEntity(baseUrl() + "/auth/registro", registro, LoginResponse.class);

        ResponseEntity<Void> conCuenta = rest.postForEntity(
                baseUrl() + "/auth/recuperar", new java.util.HashMap<>(java.util.Map.of("email", "concuenta@example.com")), Void.class);
        ResponseEntity<Void> sinCuenta = rest.postForEntity(
                baseUrl() + "/auth/recuperar", new java.util.HashMap<>(java.util.Map.of("email", "noexiste@example.com")), Void.class);

        assertThat(conCuenta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(sinCuenta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM password_reset_token", Integer.class)).isEqualTo(1);
    }

    @Test
    void confirmarRecuperacionConTokenInvalidoFalla() {
        ResponseEntity<String> respuesta = rest.postForEntity(
                baseUrl() + "/auth/recuperar/confirmar",
                java.util.Map.of("token", "token-que-no-existe", "nuevaPassword", "nuevaPassword123"),
                String.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
