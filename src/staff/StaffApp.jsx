import { Navigate, Route, Routes } from 'react-router-dom'
import StaffLayout from './StaffLayout'
import CocinaPage from './CocinaPage'
import CajaPage from './CajaPage'
import DespachosPage from './DespachosPage'
import AdminApp from './admin/AdminApp'
import { MODULOS } from './modulos'
import { useAuth } from '../context/AuthContext'

function IndexRedirect() {
  const { usuario } = useAuth()
  const primerModulo = MODULOS.find((m) => usuario && m.roles.includes(usuario.rol))
  // Sin módulo visible: StaffLayout ya muestra la pantalla de "sin acceso"
  // antes de que este Outlet se alcance a renderizar; "cocina" es solo un
  // valor por defecto inofensivo para cuando sí hay usuario pero aún no
  // resolvimos el primer módulo (no debería quedarse así).
  return <Navigate to={primerModulo ? primerModulo.path : 'cocina'} replace />
}

export default function StaffApp() {
  return (
    <Routes>
      <Route element={<StaffLayout />}>
        <Route index element={<IndexRedirect />} />
        <Route path="cocina" element={<CocinaPage />} />
        <Route path="caja" element={<CajaPage />} />
        <Route path="despachos" element={<DespachosPage />} />
        <Route path="admin/*" element={<AdminApp />} />
      </Route>
    </Routes>
  )
}
