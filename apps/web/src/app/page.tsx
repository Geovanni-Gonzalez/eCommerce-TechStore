import Link from "next/link";

export default function Home() {
  return (
    <div className="home-page">
      <section className="home-hero" aria-labelledby="titulo-inicio">
        <div className="home-hero__copy">
          <span className="home-hero__eyebrow"><span aria-hidden="true">✦</span> Bienvenido a TechStore</span>
          <h1 id="titulo-inicio">Tu próximo <span>upgrade</span> comienza aquí.</h1>
          <p className="home-hero__lead">
            Accesorios tecnológicos para descubrir, comparar y añadir a tu carrito.
            Explora nuestro catálogo de demostración con precios en colones.
          </p>
          <div className="home-hero__actions">
            <Link className="button-link" href="/productos">
              Explorar catálogo <span aria-hidden="true">↗</span>
            </Link>
            <Link className="home-hero__secondary" href="/registro">
              Crear mi cuenta <span aria-hidden="true">→</span>
            </Link>
          </div>
          <div className="home-hero__proof">
            <span><span aria-hidden="true">✓</span> Precios en ₡ CRC</span>
            <span><span aria-hidden="true">✓</span> Stock desde la API</span>
          </div>
        </div>
        <div className="home-showcase" role="img" aria-label="Ilustración conceptual de accesorios tecnológicos">
          <span className="home-showcase__orbit home-showcase__orbit--one" aria-hidden="true"/>
          <span className="home-showcase__orbit home-showcase__orbit--two" aria-hidden="true"/>
          <div className="home-showcase__label"><span className="home-showcase__signal" aria-hidden="true"/> TECH ESSENTIALS / 01</div>
          <div className="home-showcase__device" aria-hidden="true">
            <div className="home-showcase__keyboard"><div className="home-showcase__keys"/></div>
            <div className="home-showcase__mouse"><span/></div>
          </div>
          <div className="home-showcase__caption">
            <span>Diseñado para descubrir</span>
            <strong>Lo esencial, en un solo lugar.</strong>
          </div>
        </div>
      </section>

      <section className="home-benefits" aria-labelledby="benefits-title">
        <div className="home-section-heading">
          <p className="eyebrow">Todo más simple</p>
          <h2 id="benefits-title">La tecnología se disfruta desde el primer clic.</h2>
        </div>
        <div className="home-benefits__grid">
          <article className="home-benefit">
            <span className="home-benefit__icon" aria-hidden="true">₡</span>
            <h3>Precios claros</h3>
            <p>Compara accesorios con sus precios expresados en colones costarricenses.</p>
          </article>
          <article className="home-benefit">
            <span className="home-benefit__icon" aria-hidden="true">◉</span>
            <h3>Disponibilidad visible</h3>
            <p>Consulta las existencias que la API registra para cada producto del catálogo.</p>
          </article>
          <article className="home-benefit">
            <span className="home-benefit__icon" aria-hidden="true">↗</span>
            <h3>Un recorrido sencillo</h3>
            <p>Explora, inicia sesión y guarda tus productos favoritos en el carrito.</p>
          </article>
        </div>
      </section>

      <section className="home-cta" aria-labelledby="home-cta-title">
        <div>
          <p className="eyebrow">¿Qué estás buscando?</p>
          <h2 id="home-cta-title">Encuentra el accesorio que acompaña tu día.</h2>
          <p>Usa los filtros de categoría, marca y precio para ir directo a lo que necesitas.</p>
        </div>
        <Link className="button-link home-cta__button" href="/productos">Ver productos <span aria-hidden="true">→</span></Link>
      </section>
    </div>
  );
}
