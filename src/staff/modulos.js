// Compartido entre StaffLayout (nav) y StaffApp (redirect del índice) para
// no tener dos listas de "qué rol ve qué módulo" que se puedan desincronizar.
export const MODULOS = [
  { path: 'cocina', label: 'Cocina', roles: ['Cocina'] },
  { path: 'caja', label: 'Caja', roles: ['Caja'] },
  { path: 'despachos', label: 'Despachos', roles: ['Despachos', 'Administrador'] },
  { path: 'admin', label: 'Administración', roles: ['Administrador'] },
]
