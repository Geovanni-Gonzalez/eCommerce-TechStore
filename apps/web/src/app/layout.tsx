import type {Metadata} from "next";
import Link from "next/link";
import "./globals.css";
import "./auth.css";
import "./carrito/cart.css";
import AuthProvider from "@/components/AuthProvider";
import SiteNavigation from "@/components/SiteNavigation";

export const metadata: Metadata = {
  title: {default: "TechStore | Accesorios tecnológicos", template: "%s"},
  description: "Explora accesorios tecnológicos, consulta existencias y organiza tu carrito en TechStore, un prototipo académico de comercio electrónico.",
};

export default function RootLayout({children}: Readonly<{children: React.ReactNode}>) {
  return (
    <html lang="es">
      <body>
        <AuthProvider>
          <a className="skip-link" href="#contenido">Saltar al contenido</a>
          <div className="announcement">
            <div className="announcement__inner">
              <span className="announcement__dot" aria-hidden="true"/>
              <span>Experiencia de demostración académica</span>
              <span className="announcement__separator" aria-hidden="true">·</span>
              <span>No se realizan cobros reales</span>
            </div>
          </div>
          <header className="site-header">
            <div className="site-header__inner">
              <Link className="brand" href="/" aria-label="TechStore, ir al inicio">
                <span className="brand__mark" aria-hidden="true"><span/></span>
                <span>tech<span className="brand__accent">store</span><span className="brand__period">.</span></span>
              </Link>
              <SiteNavigation />
            </div>
          </header>
          <main id="contenido" className="container">{children}</main>
          <footer className="site-footer">
            <div className="site-footer__inner">
              <div className="site-footer__identity">
                <strong>tech<span className="brand__accent">store</span><span className="brand__period">.</span></strong>
                <p>Un espacio para explorar tecnología y poner a prueba una experiencia de compra digital.</p>
              </div>
              <nav aria-label="Enlaces del pie de página" className="site-footer__links">
                <Link href="/productos">Explorar productos</Link>
                <Link href="/carrito">Mi carrito</Link>
                <Link href="/cuenta">Área personal</Link>
              </nav>
            </div>
            <div className="site-footer__bottom">
              <small>© TechStore · Proyecto académico de Diseño de Software</small>
              <small>Artículos de prueba · Sin pagos ni envíos reales</small>
            </div>
          </footer>
        </AuthProvider>
      </body>
    </html>
  );
}
