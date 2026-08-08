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
        var registro = registroDe("Ana Cliente", "ana@example.com", "3000000000", "password123");
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
        var registro = registroDe("Ana Cliente", "duplicado@example.com", null, "password123");
        rest.postForEntity(baseUrl() + "/auth/registro", registro, LoginResponse.class);

        ResponseEntity<String> segundaVez =
                rest.postForEntity(baseUrl() + "/auth/registro", registro, String.class);
        assertThat(segundaVez.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void recuperarContrasenaSiempreResponde202SinRevelarSiElEmailExiste() {
        var registro = registroDe("Con Cuenta", "concuenta@example.com", null, "password123");
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

    @Test
    void sePuedeIniciarSesionConElNumeroDeDocumento() {
        var registro = registroDe("Con Documento", "condoc@example.com", null, "password123");
        rest.postForEntity(baseUrl() + "/auth/registro", registro, LoginResponse.class);

        // Mismo usuario, mismo password, pero identificándose con el documento.
        ResponseEntity<LoginResponse> respuesta = rest.postForEntity(
                baseUrl() + "/auth/login",
                new LoginRequest(registro.numeroDocumento(), "password123"),
                LoginResponse.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody().usuario().email()).isEqualTo("condoc@example.com");
    }

    @Test
    void elRegistroRechazaCorreoYContrasenaSinConfirmar() {
        var correosDistintos = new RegistroClienteRequest(
                "CC", "111222333", "Nombre", "Apellido",
                "uno@example.com", "otro@example.com", null,
                "password123", "password123", false, true);
        assertThat(rest.postForEntity(baseUrl() + "/auth/registro", correosDistintos, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        var passwordsDistintas = new RegistroClienteRequest(
                "CC", "111222334", "Nombre", "Apellido",
                "dos@example.com", "dos@example.com", null,
                "password123", "password456", false, true);
        assertThat(rest.postForEntity(baseUrl() + "/auth/registro", passwordsDistintas, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        var sinAceptarTerminos = new RegistroClienteRequest(
                "CC", "111222335", "Nombre", "Apellido",
                "tres@example.com", "tres@example.com", null,
                "password123", "password123", false, false);
        assertThat(rest.postForEntity(baseUrl() + "/auth/registro", sinAceptarTerminos, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM usuario", Integer.class)).isZero();
    }

    @Test
    void noSePuedeRegistrarDosVecesConElMismoDocumento() {
        var primero = registroDe("Primero", "primero@example.com", null, "password123");
        rest.postForEntity(baseUrl() + "/auth/registro", primero, LoginResponse.class);

        var segundo = new RegistroClienteRequest(
                "CC", primero.numeroDocumento(), "Segundo", "Apellido",
                "segundo@example.com", "segundo@example.com", null,
                "password123", "password123", false, true);

        assertThat(rest.postForEntity(baseUrl() + "/auth/registro", segundo, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void solicitarCodigoNoRevelaSiLaCuentaExisteYElCodigoEsDeUnSoloUso() {
        var registro = registroDe("Con Codigo", "concodigo@example.com", null, "password123");
        rest.postForEntity(baseUrl() + "/auth/registro", registro, LoginResponse.class);

        ResponseEntity<Void> conCuenta = rest.postForEntity(
                baseUrl() + "/auth/codigo",
                java.util.Map.of("identificador", "concodigo@example.com"), Void.class);
        ResponseEntity<Void> sinCuenta = rest.postForEntity(
                baseUrl() + "/auth/codigo",
                java.util.Map.of("identificador", "noexiste@example.com"), Void.class);

        assertThat(conCuenta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(sinCuenta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        // Solo se generó código para la cuenta que sí existe.
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM codigo_acceso", Integer.class)).isEqualTo(1);

        // El código viaja por el canal de notificaciones, no por la respuesta
        // HTTP, así que un código inventado no puede servir para entrar.
        assertThat(rest.postForEntity(baseUrl() + "/auth/codigo/login",
                java.util.Map.of("identificador", "concodigo@example.com", "codigo", "000000"),
                String.class).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT intentos FROM codigo_acceso", Integer.class)).isEqualTo(1);
    }
}
