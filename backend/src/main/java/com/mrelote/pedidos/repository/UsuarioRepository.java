package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<Usuario> findByNumeroDocumento(String numeroDocumento);
    boolean existsByNumeroDocumento(String numeroDocumento);
    Page<Usuario> findByRolId(Long rolId, Pageable pageable);

    /**
     * El cliente puede identificarse con el correo o con su número de
     * documento; ambos son únicos, así que la búsqueda es determinista.
     */
    default Optional<Usuario> findByIdentificador(String identificador) {
        return findByEmail(identificador).or(() -> findByNumeroDocumento(identificador));
    }
}
