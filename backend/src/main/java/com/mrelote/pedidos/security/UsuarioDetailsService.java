package com.mrelote.pedidos.security;

import com.mrelote.pedidos.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    /**
     * El identificador puede ser el correo o el número de documento: el
     * cliente elige con cuál entrar (ver LoginRequest). Los tokens JWT
     * siguen emitiéndose y resolviéndose por correo.
     */
    @Override
    public UserDetails loadUserByUsername(String identificador) throws UsernameNotFoundException {
        return usuarioRepository.findByIdentificador(identificador)
                .map(UsuarioPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No existe un usuario con ese correo o número de documento"));
    }
}
