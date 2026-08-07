import { useMesa } from '../context/MesaContext'

export default function MesaBanner() {
  const { loading, mesa } = useMesa()

  if (loading || !mesa) {
    return null
  }

  return (
    <div className="mesa-banner">
      Pedido en mesa — <strong>Mesa {mesa.numero}</strong>
    </div>
  )
}
