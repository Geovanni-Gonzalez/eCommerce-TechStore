import Link from "next/link";

export const metadata = { title: "Carrito | TechStore" };

export default function CarritoPage() {
  return (
    <section className="notice" aria-labelledby="titulo-carrito">
      <p className="eyebrow">Compra</p>
      <h1 id="titulo-carrito">Carrito</h1>
      <p>
        El carrito editable y sus reglas de negocio se implementarán en los
        issues del carrito. Esta página reserva su ruta de navegación.
      </p>
      <Link href="/productos">Ir a productos</Link>
    </section>
  );
}
