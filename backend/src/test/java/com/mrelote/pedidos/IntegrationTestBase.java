package com.mrelote.pedidos;

import com.mrelote.pedidos.entity.Rol;
import com.mrelote.pedidos.entity.Usuario;
import com.mrelote.pedidos.repository.RolRepository;
import com.mrelote.pedidos.repository.UsuarioRepository;
import com.mrelote.pedidos.security.RoleNames;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base para los tests de integración: levanta el contexto completo de
 * Spring (servidor HTTP real en un puerto aleatorio) contra una base
 * Postgres real (perfil "test", ver application.yml → mrelote_test).
 *
 * No usa Testcontainers: este entorno de desarrollo no tiene salida de red
 * hacia Docker Hub (el pull de la imagen de Postgres falla con 403), así
 * que en vez de un contenedor efímero por corrida se usa una base Postgres
 * persistente ya creada (`createdb mrelote_test --owner mrelote`), limpiada
 * entre tests. En un entorno con Docker Hub accesible, el reemplazo natural
 * es un `@Container` de Testcontainers con la misma imagen `postgres:16`
 * que usa el resto del proyecto.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @LocalServerPort
    protected int port;

    /**
     * No se usa el TestRestTemplate autoconfigurado por Spring Boot: por
     * defecto corre sobre HttpURLConnection, que no soporta el método PATCH
     * y falla al leer el cuerpo de respuestas de error en modo streaming
     * (401/409/422). JdkClientHttpRequestFactory (java.net.http.HttpClient,
     * incluido en el JDK) no tiene ninguno de los dos problemas.
     */
    protected final TestRestTemplate rest = new TestRestTemplate(
            new RestTemplateBuilder().requestFactory(
                    (java.util.function.Supplier<org.springframework.http.client.ClientHttpRequestFactory>) JdkClientHttpRequestFactory::new));

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected UsuarioRepository usuarioRepository;

    @Autowired
    protected RolRepository rolRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    // Todo lo que no sea catálogo de referencia sembrado por Flyway
    // (rol, permiso, rol_permiso, metodo_pago, configuracion).
    private static final String[] TABLAS_A_LIMPIAR = {
            "auditoria", "notificacion", "movimiento_caja", "caja", "despacho",
            "pago", "pedido_detalle_adicional", "pedido_detalle_opcion",
            "historial_pedido", "pedido_detalle", "pedido",
            "password_reset_token", "combo_detalle", "combo",
            "producto_adicional", "producto_opcion", "valor_opcion", "opcion",
            "adicional", "imagen_producto", "producto", "categoria",
            "qr_mesa", "mesa", "direccion", "tarifa_domicilio", "zona_domicilio",
            "cliente", "usuario",
    };

    @BeforeEach
    void limpiarBaseDeDatos() {
        for (String tabla : TABLAS_A_LIMPIAR) {
            jdbcTemplate.execute("TRUNCATE TABLE " + tabla + " RESTART IDENTITY CASCADE");
        }
    }

    protected String baseUrl() {
        return "http://localhost:" + port + "/api/v1";
    }

    /** Crea (directo en base, sin pasar por /auth/registro) un usuario de staff con el rol dado. */
    protected Usuario crearUsuarioStaff(String nombre, String email, String rolNombre) {
        Rol rol = rolRepository.findByNombre(rolNombre)
                .orElseThrow(() -> new IllegalStateException("Rol no sembrado: " + rolNombre));
        Usuario usuario = Usuario.builder()
                .rol(rol)
                .nombre(nombre)
                .email(email)
                .passwordHash(passwordEncoder.encode("password123"))
                .activo(true)
                .build();
        return usuarioRepository.save(usuario);
    }

    protected Usuario crearAdmin(String email) {
        return crearUsuarioStaff("Admin Test", email, RoleNames.ADMINISTRADOR);
    }

    /**
     * Registro de cliente con todos los campos obligatorios ya llenos y
     * coherentes (correo/contraseña confirmados, términos aceptados), para
     * que cada test solo declare lo que le importa. El documento se deriva
     * del email para que no choque con el índice único entre tests.
     */
    protected com.mrelote.pedidos.dto.request.RegistroClienteRequest registroDe(
            String nombre, String email, String telefono, String password) {
        String documento = String.valueOf(Math.abs(email.hashCode()));
        return new com.mrelote.pedidos.dto.request.RegistroClienteRequest(
                "CC", documento, nombre, "Apellido Test", email, email,
                telefono, password, password, false, true);
    }

    protected HttpEntity<Void> conToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    protected <T> HttpEntity<T> conToken(String token, T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
