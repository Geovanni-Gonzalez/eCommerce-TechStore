import Link from "next/link";

export const metadata = { title: "Productos | TechStore" };

export default function ProductosPage() {
  return (
    <section className="notice" aria-labelledby="titulo-productos">
      <p className="eyebrow">Catálogo</p>
      <h1 id="titulo-productos">Productos</h1>
      <p>
        El listado de accesorios tecnológicos se conectará a la API pública
        en BKL-018 y BKL-019. Todavía no hay productos publicados aquí.
      </p>
      <Link href="/">Volver al inicio</Link>
    </section>
  );
}
