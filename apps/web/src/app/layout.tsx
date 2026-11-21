import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";

export const metadata: Metadata = {
  title: "TechStore | Prototipo",
  description: "Prototipo académico de e-commerce de accesorios tecnológicos",
};

const navigation = [
  { href: "/", label: "Inicio" },
  { href: "/productos", label: "Productos" },
  { href: "/carrito", label: "Carrito" },
  { href: "/pedidos", label: "Mis pedidos" },
  { href: "/login", label: "Ingresar" },
] as const;

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="es">
      <body>
        <a className="skip-link" href="#contenido">
          Saltar al contenido
        </a>
        <header className="site-header">
          <div className="site-header__inner">
            <Link className="brand" href="/" aria-label="TechStore, ir al inicio">
              TechStore
            </Link>
            <nav aria-label="Navegación principal">
              <ul className="nav-list">
                {navigation.map(({ href, label }) => (
                  <li key={href}>
                    <Link href={href}>{label}</Link>
                  </li>
                ))}
              </ul>
            </nav>
          </div>
        </header>
        <main id="contenido" className="container">
          {children}
        </main>
        <footer className="site-footer">
          <div className="container">
            <small>TechStore · Prototipo académico de Diseño de Software</small>
          </div>
        </footer>
      </body>
    </html>
  );
}
