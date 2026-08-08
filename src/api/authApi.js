import { API_BASE_URL } from './config'

async function handle(response) {
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    // En los 422 de validación el backend manda la lista `detalles`
    // ("campo: mensaje"); sin esto el cliente solo vería el genérico
    // "Datos de la solicitud inválidos" sin saber qué campo corregir.
    // Se limpia el prefijo del campo, que no le dice nada al cliente
    // (isEmailConfirmado: Los correos no coinciden -> Los correos no coinciden).
    const detalle = Array.isArray(body?.detalles) && body.detalles.length > 0
      ? body.detalles.map((d) => d.replace(/^[a-zA-Z]+:\s*/, '')).join('. ')
      : null
    throw new Error(detalle || body?.mensaje || `No se pudo completar la solicitud (${response.status})`)
  }
  return response.json()
}

function post(path, payload) {
  return fetch(`${API_BASE_URL}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

/** `identificador` puede ser el correo o el número de documento. */
export function login(identificador, password) {
  return post('/auth/login', { identificador, password }).then(handle)
}

export function registro(datos) {
  return post('/auth/registro', {
    tipoDocumento: datos.tipoDocumento,
    numeroDocumento: datos.numeroDocumento,
    nombre: datos.nombre,
    apellidos: datos.apellidos,
    email: datos.email,
    confirmarEmail: datos.confirmarEmail,
    telefono: datos.telefono || null,
    password: datos.password,
    confirmarPassword: datos.confirmarPassword,
    aceptaPromociones: Boolean(datos.aceptaPromociones),
    aceptaTerminos: Boolean(datos.aceptaTerminos),
  }).then(handle)
}

/** Pide un código de un solo uso. Responde 202 sin cuerpo. */
export async function solicitarCodigo(identificador) {
  const response = await post('/auth/codigo', { identificador })
  if (!response.ok) {
    throw new Error('No se pudo solicitar el código')
  }
}

export function loginConCodigo(identificador, codigo) {
  return post('/auth/codigo/login', { identificador, codigo }).then(handle)
}

/** Recuperación de contraseña. Responde 202 sin cuerpo. */
export async function solicitarRecuperacion(email) {
  const response = await post('/auth/recuperar', { email })
  if (!response.ok) {
    throw new Error('No se pudo enviar el enlace de recuperación')
  }
}
