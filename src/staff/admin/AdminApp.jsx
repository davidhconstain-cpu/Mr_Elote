import { NavLink, Route, Routes } from 'react-router-dom'
import ProductosAdminPage from './ProductosAdminPage'
import CategoriasAdminPage from './CategoriasAdminPage'
import CombosAdminPage from './CombosAdminPage'
import ExtrasAdminPage from './ExtrasAdminPage'
import UsuariosAdminPage from './UsuariosAdminPage'
import RolesAdminPage from './RolesAdminPage'
import DomiciliosAdminPage from './DomiciliosAdminPage'
import MesasAdminPage from './MesasAdminPage'
import InformesAdminPage from './InformesAdminPage'
import AuditoriaAdminPage from './AuditoriaAdminPage'
import ConfiguracionAdminPage from './ConfiguracionAdminPage'

const TABS = [
  { path: 'productos', label: 'Productos' },
  { path: 'categorias', label: 'Categorías' },
  { path: 'combos', label: 'Combos' },
  { path: 'extras', label: 'Opciones/Adicionales' },
  { path: 'usuarios', label: 'Usuarios' },
  { path: 'roles', label: 'Roles' },
  { path: 'domicilios', label: 'Domicilios' },
  { path: 'mesas', label: 'Mesas' },
  { path: 'informes', label: 'Informes' },
  { path: 'auditoria', label: 'Auditoría' },
  { path: 'configuracion', label: 'Configuración' },
]

export default function AdminApp() {
  return (
    <div>
      <h1 className="staff-page-title">Administración</h1>
      <div className="staff-tabs">
        {TABS.map((t) => (
          <NavLink key={t.path} to={`/staff/admin/${t.path}`} className={({ isActive }) => (isActive ? 'active' : '')}>
            {t.label}
          </NavLink>
        ))}
      </div>
      <Routes>
        <Route path="productos" element={<ProductosAdminPage />} />
        <Route path="categorias" element={<CategoriasAdminPage />} />
        <Route path="combos" element={<CombosAdminPage />} />
        <Route path="extras" element={<ExtrasAdminPage />} />
        <Route path="usuarios" element={<UsuariosAdminPage />} />
        <Route path="roles" element={<RolesAdminPage />} />
        <Route path="domicilios" element={<DomiciliosAdminPage />} />
        <Route path="mesas" element={<MesasAdminPage />} />
        <Route path="informes" element={<InformesAdminPage />} />
        <Route path="auditoria" element={<AuditoriaAdminPage />} />
        <Route path="configuracion" element={<ConfiguracionAdminPage />} />
        <Route path="*" element={<ProductosAdminPage />} />
      </Routes>
    </div>
  )
}
