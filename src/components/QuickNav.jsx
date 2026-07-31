export default function QuickNav({ links }) {
  return (
    <nav className="quick-nav" aria-label="Categorías del menú">
      {links.map((link) => (
        <a key={link.id} href={`#${link.id}`} className="quick-nav-chip">
          {link.label}
        </a>
      ))}
    </nav>
  )
}
