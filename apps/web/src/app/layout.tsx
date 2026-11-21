import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";
import "./auth.css";
import "./carrito/cart.css";
import AuthProvider from "@/components/AuthProvider";
import AccountNav from "@/components/AccountNav";

export const metadata: Metadata = {
  title: "TechStore | Prototipo",
  description: "Prototipo académico de e-commerce de accesorios tecnológicos",
};

const navigation = [
  { href: "/", label: "Inicio" },
  { href: "/productos", label: "Productos" },
  { href: "/carrito", label: "Carrito" },
  { href: "/pedidos", label: "Mis pedidos" },

] as const;

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="es">
      <body>
        <AuthProvider>
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
                <AccountNav />
                <li><Link href="/registro">Crear cuenta</Link></li>
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
        </AuthProvider>
      </body>
    </html>
  );
}
