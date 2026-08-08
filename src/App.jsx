import { Route, Routes } from 'react-router-dom'
import CatalogApp from './CatalogApp'
import StaffApp from './staff/StaffApp'
import { AuthProvider } from './context/AuthContext'
import './App.css'
import './staff/staff.css'

function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/staff/*" element={<StaffApp />} />
        <Route path="/*" element={<CatalogApp />} />
      </Routes>
    </AuthProvider>
  )
}

export default App
