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
        Estamos preparando el catálogo, el carrito y el acceso a cuentas.
        Esta versión presenta la navegación y la estructura visual sobre la
        cual construiremos las funcionalidades.
      </p>
      <Link className="button-link" href="/productos">
        Explorar catálogo
      </Link>
    </section>
  );
}
