package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registro público de cliente. Las confirmaciones de correo y contraseña y
 * la aceptación de términos se validan también aquí, no solo en el
 * navegador: el endpoint es público y cualquiera puede llamarlo directo.
 */
public record RegistroClienteRequest(
        @NotBlank(message = "El tipo de documento es obligatorio") String tipoDocumento,
        @NotBlank(message = "El número de documento es obligatorio")
        @Size(max = 30, message = "El número de documento es demasiado largo") String numeroDocumento,
        @NotBlank(message = "Los nombres son obligatorios") String nombre,
        @NotBlank(message = "Los apellidos son obligatorios") String apellidos,
        @NotBlank @Email(message = "El correo no tiene un formato válido") String email,
        @NotBlank(message = "Debes confirmar el correo") String confirmarEmail,
        String telefono,
        @NotBlank @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres") String password,
        @NotBlank(message = "Debes confirmar la contraseña") String confirmarPassword,
        Boolean aceptaPromociones,
        Boolean aceptaTerminos) {

    @AssertTrue(message = "Los correos no coinciden")
    public boolean isEmailConfirmado() {
        return email != null && email.equalsIgnoreCase(confirmarEmail);
    }

    @AssertTrue(message = "Las contraseñas no coinciden")
    public boolean isPasswordConfirmada() {
        return password != null && password.equals(confirmarPassword);
    }

    @AssertTrue(message = "Debes aceptar los términos y condiciones")
    public boolean isTerminosAceptados() {
        return Boolean.TRUE.equals(aceptaTerminos);
    }
}
