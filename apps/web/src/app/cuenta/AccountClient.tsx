"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/AuthProvider";

export default function AccountClient() {
  const router = useRouter();
  const { user, logout } = useAuth();
  if (!user) {
    return (
      <section className="auth-card" aria-labelledby="account-title">
        <h1 id="account-title">Acceso privado</h1>
        <p>Inicia sesión para consultar tu cuenta.</p>
        <Link href="/login">Ir a iniciar sesión</Link>
      </section>
    );
  }
  if (user.role === "ADMIN") {
    return (
      <section className="auth-card" aria-labelledby="account-title">
        <h1 id="account-title">Cuenta de administrador</h1>
        <Link href="/admin">Ir a acceso administrativo</Link>
      </section>
    );
  }
  return (
    <section className="auth-card" aria-labelledby="account-title">
      <p className="eyebrow">Acceso cliente</p>
      <h1 id="account-title">Mi cuenta</h1>
      <p className="auth-welcome">Bienvenido, {user.displayName}.</p>
      <dl className="auth-profile">
        <div><dt>Correo</dt><dd>{user.email}</dd></div>
        <div><dt>Rol</dt><dd>Cliente</dd></div>
      </dl>
      <p><Link href="/productos">Explorar productos</Link></p>
      <p><Link href="/pedidos">Consultar mis pedidos</Link></p>
      <p className="auth-help">El módulo de historial existente todavía solicita
        una verificación propia; su integración con esta sesión se completará
        junto al flujo visual del carrito y checkout.</p>
      <button type="button" className="auth-button" onClick={() => {
        void logout().then(() => router.replace("/login"));
      }}>Cerrar sesión</button>
    </section>
  );
}
