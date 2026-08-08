import { useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { solicitarCodigo, solicitarRecuperacion } from '../api/authApi'

const TIPOS_DOCUMENTO = [
  { value: 'CC', label: 'Cédula de ciudadanía' },
  { value: 'CE', label: 'Cédula de extranjería' },
  { value: 'TI', label: 'Tarjeta de identidad' },
  { value: 'PAS', label: 'Pasaporte' },
  { value: 'NIT', label: 'NIT' },
]

const REGISTRO_VACIO = {
  tipoDocumento: '',
  numeroDocumento: '',
  nombre: '',
  apellidos: '',
  email: '',
  confirmarEmail: '',
  telefono: '',
  password: '',
  confirmarPassword: '',
  aceptaPromociones: false,
  aceptaTerminos: false,
}

/** Campo de contraseña con el ojito para mostrar/ocultar. */
function PasswordInput({ value, onChange, placeholder, required, minLength }) {
  const [visible, setVisible] = useState(false)
  return (
    <div className="auth-password-field">
      <input
        type={visible ? 'text' : 'password'}
        required={required}
        minLength={minLength}
        placeholder={placeholder}
        value={value}
        onChange={onChange}
      />
      <button
        type="button"
        className="auth-password-toggle"
        onClick={() => setVisible((v) => !v)}
        aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}
      >
        {visible ? '🙈' : '👁'}
      </button>
    </div>
  )
}

export default function AuthModal() {
  const { login, loginConCodigo, registro, closeAuthModal } = useAuth()
  const [tab, setTab] = useState('login')
  const [metodo, setMetodo] = useState('password') // password | codigo
  const [identificador, setIdentificador] = useState('')
  const [password, setPassword] = useState('')
  const [codigo, setCodigo] = useState('')
  const [codigoEnviado, setCodigoEnviado] = useState(false)
  const [registroForm, setRegistroForm] = useState(REGISTRO_VACIO)
  const [status, setStatus] = useState('idle')
  const [error, setError] = useState(null)
  const [aviso, setAviso] = useState(null)

  function campo(nombre) {
    return (e) =>
      setRegistroForm((prev) => ({
        ...prev,
        [nombre]: e.target.type === 'checkbox' ? e.target.checked : e.target.value,
      }))
  }

  async function ejecutar(accion) {
    setStatus('sending')
    setError(null)
    try {
      await accion()
    } catch (err) {
      setError(err.message)
    } finally {
      setStatus('idle')
    }
  }

  const handleLogin = (e) => {
    e.preventDefault()
    ejecutar(() => login(identificador, password))
  }

  const handleEnviarCodigo = () =>
    ejecutar(async () => {
      await solicitarCodigo(identificador)
      setCodigoEnviado(true)
      setAviso('Si la cuenta existe, te enviamos un código. Revisa tu correo.')
    })

  const handleLoginCodigo = (e) => {
    e.preventDefault()
    ejecutar(() => loginConCodigo(identificador, codigo))
  }

  const handleOlvide = () =>
    ejecutar(async () => {
      if (!identificador.includes('@')) {
        throw new Error('Escribe tu correo electrónico para enviarte el enlace.')
      }
      await solicitarRecuperacion(identificador)
      setAviso('Si la cuenta existe, te enviamos un enlace para restablecer tu contraseña.')
    })

  const handleRegistro = (e) => {
    e.preventDefault()
    ejecutar(() => registro(registroForm))
  }

  function cambiarTab(nuevo) {
    setTab(nuevo)
    setError(null)
    setAviso(null)
  }

  const enviando = status === 'sending'

  return (
    <div className="auth-overlay" onClick={closeAuthModal}>
      <div
        className={`auth-modal${tab === 'registro' ? ' auth-modal-ancho' : ''}`}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="auth-modal-header">
          <h2 className="auth-title">{tab === 'login' ? 'Inicia sesión' : 'Regístrate'}</h2>
          <button type="button" className="cart-close" onClick={closeAuthModal} aria-label="Cerrar">
            ✕
          </button>
        </div>

        {tab === 'login' ? (
          <form className="auth-form" onSubmit={metodo === 'codigo' ? handleLoginCodigo : handleLogin}>
            <label className="auth-label" htmlFor="auth-identificador">
              Correo o número de documento
            </label>
            <input
              id="auth-identificador"
              required
              placeholder="correo@ejemplo.com o 1234567890"
              value={identificador}
              onChange={(e) => setIdentificador(e.target.value)}
            />

            <p className="auth-pregunta">¿Cómo prefieres iniciar sesión?</p>
            <div className="auth-metodo-toggle">
              <button
                type="button"
                className={metodo === 'password' ? 'active' : ''}
                onClick={() => setMetodo('password')}
              >
                Ingresa tu contraseña
              </button>
              <button
                type="button"
                className={metodo === 'codigo' ? 'active' : ''}
                onClick={() => setMetodo('codigo')}
              >
                Recibe un código
              </button>
            </div>

            {metodo === 'password' ? (
              <>
                <PasswordInput
                  required
                  placeholder="Contraseña"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
                <button type="button" className="auth-link" onClick={handleOlvide} disabled={enviando}>
                  ¿Olvidaste tu contraseña?
                </button>
              </>
            ) : (
              <>
                {codigoEnviado && (
                  <input
                    required
                    inputMode="numeric"
                    maxLength={6}
                    placeholder="Código de 6 dígitos"
                    value={codigo}
                    onChange={(e) => setCodigo(e.target.value)}
                  />
                )}
                <button
                  type="button"
                  className="auth-link"
                  onClick={handleEnviarCodigo}
                  disabled={enviando || !identificador}
                >
                  {codigoEnviado ? 'Reenviar código' : 'Enviarme el código'}
                </button>
              </>
            )}

            {aviso && <p className="auth-aviso">{aviso}</p>}
            {error && <p className="cart-error">{error}</p>}

            <button
              type="submit"
              className="cart-confirm-button"
              disabled={enviando || (metodo === 'codigo' && !codigoEnviado)}
            >
              {enviando ? 'Entrando…' : 'Inicia sesión'}
            </button>
            <button type="button" className="auth-secondary-button" onClick={() => cambiarTab('registro')}>
              Regístrate
            </button>
          </form>
        ) : (
          <form className="auth-form" onSubmit={handleRegistro}>
            <p className="auth-subtitulo">Y comienza a disfrutar de todos los beneficios</p>

            <div className="auth-grid">
              <label className="auth-field">
                <span className="auth-label">Tipo de documento</span>
                <select required value={registroForm.tipoDocumento} onChange={campo('tipoDocumento')}>
                  <option value="">Selecciona…</option>
                  {TIPOS_DOCUMENTO.map((t) => (
                    <option key={t.value} value={t.value}>
                      {t.label}
                    </option>
                  ))}
                </select>
              </label>

              <label className="auth-field">
                <span className="auth-label">Número de documento</span>
                <input required value={registroForm.numeroDocumento} onChange={campo('numeroDocumento')} />
              </label>

              <label className="auth-field">
                <span className="auth-label">Nombres</span>
                <input required value={registroForm.nombre} onChange={campo('nombre')} />
              </label>

              <label className="auth-field">
                <span className="auth-label">Apellidos</span>
                <input required value={registroForm.apellidos} onChange={campo('apellidos')} />
              </label>

              <label className="auth-field">
                <span className="auth-label">Correo</span>
                <input type="email" required value={registroForm.email} onChange={campo('email')} />
              </label>

              <label className="auth-field">
                <span className="auth-label">Confirmar correo</span>
                <input
                  type="email"
                  required
                  value={registroForm.confirmarEmail}
                  onChange={campo('confirmarEmail')}
                />
              </label>

              <label className="auth-field">
                <span className="auth-label">Teléfono (opcional)</span>
                <input value={registroForm.telefono} onChange={campo('telefono')} />
              </label>

              <label className="auth-field">
                <span className="auth-label">Contraseña</span>
                <PasswordInput
                  required
                  minLength={8}
                  placeholder="Mínimo 8 caracteres"
                  value={registroForm.password}
                  onChange={campo('password')}
                />
              </label>

              <label className="auth-field">
                <span className="auth-label">Confirmar contraseña</span>
                <PasswordInput
                  required
                  minLength={8}
                  value={registroForm.confirmarPassword}
                  onChange={campo('confirmarPassword')}
                />
              </label>
            </div>

            <label className="auth-check">
              <input
                type="checkbox"
                checked={registroForm.aceptaPromociones}
                onChange={campo('aceptaPromociones')}
              />
              <span>Deseo recibir correos de las promociones de Mr. Elote</span>
            </label>

            <label className="auth-check">
              <input
                type="checkbox"
                required
                checked={registroForm.aceptaTerminos}
                onChange={campo('aceptaTerminos')}
              />
              <span>Acepto términos y condiciones y uso de este sitio web</span>
            </label>

            {error && <p className="cart-error">{error}</p>}

            <button type="submit" className="cart-confirm-button" disabled={enviando}>
              {enviando ? 'Creando cuenta…' : 'Registrarme'}
            </button>
            <button type="button" className="auth-secondary-button" onClick={() => cambiarTab('login')}>
              Ya tengo cuenta
            </button>
          </form>
        )}
      </div>
    </div>
  )
}
