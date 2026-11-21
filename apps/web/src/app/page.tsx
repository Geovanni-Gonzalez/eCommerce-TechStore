import Link from "next/link";

export default function Home() {
  return (
    <section className="hero" aria-labelledby="titulo-inicio">
      <p className="eyebrow">Proyecto #3 · Diseño de Software</p>
      <h1 id="titulo-inicio">Tecnología al alcance de todos</h1>
      <p className="lead">
        Bienvenido a TechStore, nuestro prototipo de tienda de accesorios
        tecnológicos.
      </p>
      <p>
        Ya puedes explorar productos y consultar precios y existencias reales
        desde la API. El carrito editable y el acceso general a cuentas
        continúan en desarrollo.
      </p>
      <Link className="button-link" href="/productos">
        Explorar catálogo
      </Link>
    </section>
  );
}
