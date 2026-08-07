package com.mrelote.pedidos.security;

/**
 * Nombres canónicos de rol, tal como quedan sembrados en la tabla rol
 * (ver db/migration/V2__seed_roles.sql). Coinciden con los actores de la
 * sección 5 de la especificación de requisitos.
 */
public final class RoleNames {

    public static final String CLIENTE = "Cliente";
    public static final String MESERO = "Mesero";
    public static final String CAJA = "Caja";
    public static final String COCINA = "Cocina";
    public static final String DESPACHOS = "Despachos";
    public static final String ADMINISTRADOR = "Administrador";

    private RoleNames() {
    }
}
