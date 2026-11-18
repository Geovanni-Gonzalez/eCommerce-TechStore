import Link from "next/link";

export const metadata = { title: "Ingresar | TechStore" };

export default function LoginPage() {
  return (
    <section className="notice" aria-labelledby="titulo-login">
      <p className="eyebrow">Cuenta</p>
      <h1 id="titulo-login">Ingresar</h1>
      <p>
        El inicio de sesión seguro se incorporará con los issues de
        autenticación. Aún no hay un formulario habilitado.
      </p>
      <Link href="/">Volver al inicio</Link>
    </section>
  );
}
